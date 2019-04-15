// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class A {
  void foo() {}
  void iCallFoo() {
    foo();
  }
}
class B extends A {
  @Override
  void foo() {
    m();
  }
  void m() {} //<--- call hierarchy here
}
class C extends A {
  void indirectICallFooFromC() {
    iCallFoo();
  }
  @Override
  void foo() {
    indirectICallFooFromC();
  }
}
class D {
  void a(A a) {
    a.foo();
  }
  void c(C c) {
    c.foo();
  }
  void b(B b) {
    b.foo();
  }
}