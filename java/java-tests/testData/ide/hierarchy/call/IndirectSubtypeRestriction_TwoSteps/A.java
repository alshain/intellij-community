// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class User {
  void interestingUse() {
    new InterestingProcessor().processAll();
  }

  void noUse() {
    new OtherProcessor().processAll();
  }
}

abstract class Processor {
  abstract void processItem();
  void processAll() {
    // complex iteration logic
    // foreach
    processItemIndirect();
    // endfor
  }

  void processItemIndirect() {
    processItem();
  }
}

class InterestingProcessor extends Processor {
  @Override
  void processItem() {

  }
}

class OtherProcessor extends Processor {
  @Override
  void processItem() {

  }
}