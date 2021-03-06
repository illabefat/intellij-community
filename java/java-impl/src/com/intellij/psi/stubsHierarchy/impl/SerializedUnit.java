/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.stubsHierarchy.impl.Symbol.ClassSymbol;
import com.intellij.psi.stubsHierarchy.impl.Symbol.MemberSymbol;
import com.intellij.psi.stubsHierarchy.impl.Symbol.PackageSymbol;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import com.intellij.util.io.UnsyncByteArrayOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
class SerializedUnit {
  private final byte[] myBytes;

  SerializedUnit(byte[] bytes) {
    myBytes = bytes;
  }

  SerializedUnit(IndexTree.Unit unit) {
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      UnsyncByteArrayOutputStream stream = new UnsyncByteArrayOutputStream();
      writeUnit(new DataOutputStream(stream), unit);
      myBytes = stream.toByteArray();
    }
    catch (IOException impossible) {
      throw new RuntimeException(impossible);
    }
  }

  byte[] getSerializedBytes() {
    return myBytes;
  }

  void readUnit(StubEnter stubEnter, int fileId) {
    try {
      enterUnit(new UnitInputStream(new UnsyncByteArrayInputStream(myBytes), fileId, stubEnter));
    }
    catch (IOException impossible) {
      throw new RuntimeException(impossible);
    }
  }

  /**
   * @see NameEnvironment#readQualifiedName(DataInput)
   */
  static void writeQualifiedName(DataOutput out, @QNameId int[] array) throws IOException {
    DataInputOutputUtil.writeINT(out, array.length);
    for (int i : array) {
      out.writeInt(i);
    }
  }

  // unit

  private static void writeUnit(@NotNull DataOutput out, IndexTree.Unit value) throws IOException {
    writeQualifiedName(out, value.myPackageName);
    out.writeByte(value.myUnitType);
    if (value.myUnitType != IndexTree.BYTECODE) {
      Imports.writeImports(out, value);
    }
    // class Declaration
    DataInputOutputUtil.writeINT(out, value.myDecls.length);
    for (IndexTree.ClassDecl def : value.myDecls) {
      saveClassDecl(out, def);
    }
  }

  private static void enterUnit(UnitInputStream in) throws IOException {
    PackageSymbol pkg = in.stubEnter.enterPackage(in);
    byte type = in.readByte();
    long[] imports = type == IndexTree.BYTECODE ? Imports.EMPTY_ARRAY : Imports.readImports(in);
    UnitInfo unitInfo = UnitInfo.mkUnitInfo(type, imports);

    int classCount = DataInputOutputUtil.readINT(in);
    for (int i = 0; i < classCount; i++) {
      readClassDecl(in, unitInfo, pkg, pkg.myQualifiedName);
    }
  }

  // class

  private static void saveClassDecl(@NotNull DataOutput out, IndexTree.ClassDecl value) throws IOException {
    DataInputOutputUtil.writeINT(out, value.myStubId);
    DataInputOutputUtil.writeINT(out, value.myMods);
    out.writeInt(value.myName);
    writeSupers(out, value);
    writeMembers(out, value.myDecls);
  }

  private static ClassSymbol readClassDecl(UnitInputStream in, UnitInfo info, Symbol owner, @QNameId int ownerName) throws IOException {
    int stubId = DataInputOutputUtil.readINT(in);
    int mods = DataInputOutputUtil.readINT(in);
    @ShortName int name = in.readInt();
    @QNameId int[] superNames = readSupers(in);

    @QNameId int qname = in.names.memberQualifiedName(ownerName, name);
    ClassSymbol symbol = in.stubEnter.classEnter(info, owner, stubId, mods, name, superNames, qname, in.fileId);

    readMembers(in, info, qname, symbol);
    return symbol;
  }

  // supers

  private static void writeSupers(@NotNull DataOutput out, IndexTree.ClassDecl value) throws IOException {
    DataInputOutputUtil.writeINT(out, value.mySupers.length);
    for (int[] aSuper : value.mySupers) {
      writeQualifiedName(out, aSuper);
    }
  }

  private static @QNameId int[] readSupers(UnitInputStream in) throws IOException {
    @QNameId int[] superNames = new int[DataInputOutputUtil.readINT(in)];
    for (int i = 0; i < superNames.length; i++) {
      superNames[i] = in.names.readQualifiedName(in);
    }
    return superNames;
  }

  // members

  private static void writeMembers(@NotNull DataOutput out, IndexTree.Decl[] decls) throws IOException {
    DataInputOutputUtil.writeINT(out, decls.length);
    for (IndexTree.Decl def : decls) {
      saveDecl(out, def);
    }
  }

  private static void readMembers(UnitInputStream in,
                                  UnitInfo info,
                                  @QNameId int ownerName,
                                  MemberSymbol symbol) throws IOException {
    int memberCount = DataInputOutputUtil.readINT(in);
    if (memberCount == 0) return;

    List<ClassSymbol> members = new ArrayList<>();
    for (int i = 0; i < memberCount; i++) {
      ContainerUtil.addIfNotNull(members, readDecl(in, info, symbol, ownerName));
    }
    symbol.setMembers(members);
  }

  // decl: class or member

  private static void saveDecl(@NotNull DataOutput out, IndexTree.Decl value) throws IOException {
    if (value instanceof IndexTree.ClassDecl) {
      out.writeBoolean(true);
      saveClassDecl(out, (IndexTree.ClassDecl)value);
    } else if (value instanceof IndexTree.MemberDecl) {
      out.writeBoolean(false);
      writeMembers(out, ((IndexTree.MemberDecl)value).myDecls);
    }
  }

  private static ClassSymbol readDecl(UnitInputStream in, UnitInfo info, Symbol owner, @QNameId int ownerName) throws IOException {
    if (in.readBoolean()) {
      return readClassDecl(in, info, owner, ownerName);
    }

    readMembers(in, info, ownerName, new MemberSymbol(owner));
    return null;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof SerializedUnit && Arrays.equals(myBytes, ((SerializedUnit)o).myBytes);
  }

  @Override
  public int hashCode() {
    int result = myBytes.length;
    int length = Math.min(30, myBytes.length);
    for (int i = 0; i < length; i++) {
      result = 31 * result + myBytes[i];
    }
    return result;
  }
}

class UnitInputStream extends DataInputStream {
  final int fileId;
  final StubEnter stubEnter;
  final NameEnvironment names;

  UnitInputStream(InputStream in, int fileId, StubEnter stubEnter) {
    super(in);
    this.fileId = fileId;
    this.stubEnter = stubEnter;
    this.names = stubEnter.myNameEnvironment;
  }
}
