/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.refactoring.convertTopLevelFunction.PyConvertLocalFunctionToTopLevelFunctionAction;

/**
 * @author Mikhail Golubev
 */
public class PyConvertLocalFunctionToTopLevelFunctionTest extends PyTestCase {

  public void doTest() {
    myFixture.configureByFile(getTestName(true) + ".py");
    myFixture.testAction(new PyConvertLocalFunctionToTopLevelFunctionAction());
    myFixture.checkResultByFile(getTestName(true) + ".after.py");
  }

  // PY-6637
  public void testSimple() {
    doTest();
  }

  // PY-6637
  public void testLocalFunctionDetection() {
    myFixture.configureByFile(getTestName(true) + ".py");
    moveByText("func");
    assertFalse(myFixture.testAction(new PyConvertLocalFunctionToTopLevelFunctionAction()).isEnabled());
    moveByText("local");
    assertTrue(myFixture.testAction(new PyConvertLocalFunctionToTopLevelFunctionAction()).isEnabled());
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/convertTopLevel/";
  }
}
