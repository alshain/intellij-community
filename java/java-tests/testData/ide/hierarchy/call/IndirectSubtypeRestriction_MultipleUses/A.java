// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class User {
  void interestingUse() {
    new InterestingProcessor().processAllIndirect();
  }

  void interestingUseAlso() {
    new OtherProcessor().processAllIndirect();
  }
}

abstract class Processor {
  abstract void processItem();
  void processAll() {
    // complex iteration logic
    // foreach
    processItem();
    // endfor
  }

  void processAllIndirect() {
    // complex iteration logic
    // foreach
    processAll();
    Processor otherProcessor = new InterestingProcessor();
    otherProcessor.processAll();
    // endfor
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