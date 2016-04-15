/*
 * SonarQube Java
 * Copyright (C) 2012-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.resolve;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.sonar.java.resolve.JavaSymbol.TypeJavaSymbol;
import org.sonar.java.resolve.targets.Annotations;
import org.sonar.java.resolve.targets.AnonymousClass;
import org.sonar.java.resolve.targets.HasInnerClass;
import org.sonar.java.resolve.targets.InnerClassBeforeOuter;
import org.sonar.java.resolve.targets.NamedClassWithinMethod;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.semantic.SymbolMetadata;
import org.sonar.plugins.java.api.semantic.Type;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;

public class BytecodeCompleterTest {

  //used to load classes in same package
  public BytecodeCompleterPackageVisibility bytecodeCompleterPackageVisibility = new BytecodeCompleterPackageVisibility();
  private BytecodeCompleter bytecodeCompleter;

  private void accessPackageVisibility() {
    bytecodeCompleterPackageVisibility.add(1, 2);
  }

  @Before
  public void setUp() throws Exception {
    bytecodeCompleter = new BytecodeCompleter(Lists.newArrayList(new File("target/test-classes"), new File("target/classes")), new ParametrizedTypeCache());
    new Symbols(bytecodeCompleter);

  }

  @Test
  public void class_names_ending_with_$() throws Exception {
    JavaSymbol.TypeJavaSymbol classSymbol = bytecodeCompleter.getClassSymbol("org/sonar/java/resolve/targets/OuterClassEndingWith$$InnerClassEndingWith$");
    assertThat(classSymbol.getName()).isEqualTo("InnerClassEndingWith$");
    assertThat(classSymbol.owner().getName()).isEqualTo("OuterClassEndingWith$");
  }

  @Test
  public void innerClassWithDollarName() throws Exception {
    JavaSymbol.TypeJavaSymbol classSymbol = bytecodeCompleter.getClassSymbol("org/sonar/java/resolve/targets/UseDollarNames");
    //Complete superclass which has an inner class named A$Bq
    JavaType superclass = classSymbol.getSuperclass();
    JavaType superSuperClass = superclass.getSymbol().getSuperclass();
    assertThat(superSuperClass.fullyQualifiedName()).isEqualTo("java.lang.Object");
    Collection<Symbol> symbols = superclass.getSymbol().memberSymbols();
    for (Symbol symbol : symbols) {
      if(symbol.isTypeSymbol()) {
        assertThat(symbol.name()).isEqualTo("A$B");
        Collection<Symbol> members = ((Symbol.TypeSymbol) symbol).lookupSymbols("C$D");
        assertThat(members).isNotEmpty();
      }
    }
  }

  @Test
  public void annotations() throws Exception {
    bytecodeCompleter.getClassSymbol(Annotations.class.getName().replace('.', '/')).complete();
  }

  @Test
  public void anonymous_class() {
    bytecodeCompleter.getClassSymbol(AnonymousClass.class.getName().replace('.', '/')).complete();
  }

  @Test
  public void named_class_within_method() {
    bytecodeCompleter.getClassSymbol(NamedClassWithinMethod.class.getName().replace('.', '/')).complete();
  }

  @Test
  public void inner_class_before_outer() {
    JavaSymbol.TypeJavaSymbol symbol = bytecodeCompleter.getClassSymbol(InnerClassBeforeOuter.class.getName());
    JavaSymbol.TypeJavaSymbol innerClass = symbol.getSuperclass().symbol;
    JavaSymbol.TypeJavaSymbol outerClass = (JavaSymbol.TypeJavaSymbol) innerClass.owner();
    assertThat(outerClass.members().lookup(HasInnerClass.InnerClass.class.getSimpleName())).containsExactly(innerClass);
  }

  @Test
  public void outer_class_before_inner() {
    JavaSymbol.TypeJavaSymbol outerClass = bytecodeCompleter.getClassSymbol(HasInnerClass.class.getName());
    assertThat(outerClass.members().lookup(HasInnerClass.InnerClass.class.getSimpleName())).hasSize(1);
  }

  @Test
  public void completing_symbol_ArrayList() throws Exception {
    JavaSymbol.TypeJavaSymbol arrayList = bytecodeCompleter.getClassSymbol("java/util/ArrayList");
    //Check supertype
    assertThat(arrayList.getSuperclass().symbol.name).isEqualTo("AbstractList");
    assertThat(arrayList.getSuperclass().symbol.owner().name).isEqualTo("java.util");

    //Check interfaces
    assertThat(arrayList.getInterfaces()).hasSize(4);
    List<String> interfacesName = Lists.newArrayList();
    for (JavaType interfaceType : arrayList.getInterfaces()) {
      interfacesName.add(interfaceType.symbol.name);
    }
    assertThat(interfacesName).hasSize(4);
    assertThat(interfacesName).contains("List", "RandomAccess", "Cloneable", "Serializable");
  }

  @Test
  public void symbol_type_in_same_package_should_be_resolved() throws Exception {
    JavaSymbol.TypeJavaSymbol thisTest = bytecodeCompleter.getClassSymbol(Convert.bytecodeName(getClass().getName()));
    List<JavaSymbol> symbols = thisTest.members().lookup("bytecodeCompleterPackageVisibility");
    assertThat(symbols).hasSize(1);
    JavaSymbol.VariableJavaSymbol symbol = (JavaSymbol.VariableJavaSymbol) symbols.get(0);
    assertThat(symbol.type.symbol.name).isEqualTo("BytecodeCompleterPackageVisibility");
    assertThat(symbol.type.symbol.owner().name).isEqualTo(thisTest.owner().name);
  }

  @Test
  public void void_method_type_should_be_resolved() {
    JavaSymbol.TypeJavaSymbol thisTest = bytecodeCompleter.getClassSymbol(Convert.bytecodeName(getClass().getName()));
    List<JavaSymbol> symbols = thisTest.members().lookup("bytecodeCompleterPackageVisibility");
    assertThat(symbols).hasSize(1);
    JavaSymbol.VariableJavaSymbol symbol = (JavaSymbol.VariableJavaSymbol) symbols.get(0);
    symbols = symbol.getType().symbol.members().lookup("voidMethod");
    assertThat(symbols).hasSize(1);
    JavaSymbol method = symbols.get(0);
    assertThat(method.type).isInstanceOf(MethodJavaType.class);
    assertThat(((MethodJavaType) method.type).resultType.symbol.name).isEqualTo("void");
  }

  @Test
  public void inner_class_should_be_correctly_flagged() {
    JavaSymbol.TypeJavaSymbol interfaceWithInnerEnum = bytecodeCompleter.getClassSymbol("org.sonar.java.resolve.targets.subpackage.FlagCompletion");
    List<JavaSymbol> members = interfaceWithInnerEnum.members().lookup("bar");
    JavaSymbol.TypeJavaSymbol innerEnum = ((JavaSymbol.MethodJavaSymbol) members.get(0)).getReturnType();
    //complete outer class
    innerEnum.owner().complete();
    //verify flag are set for inner class.
    assertThat(innerEnum.isEnum()).isTrue();
    assertThat(innerEnum.isPublic()).isTrue();
    assertThat(innerEnum.isStatic()).isTrue();
    assertThat(innerEnum.isFinal()).isTrue();
  }

  @Test
  public void deprecated_classes_should_be_flagged() throws Exception {
    JavaSymbol.TypeJavaSymbol deprecatedClass = bytecodeCompleter.getClassSymbol("org.sonar.java.resolve.targets.DeprecatedClass");
    assertThat(deprecatedClass.isDeprecated()).isTrue();
    JavaSymbol.TypeJavaSymbol staticInnerClass = (JavaSymbol.TypeJavaSymbol) deprecatedClass.members().lookup("StaticInnerClass").get(0);
    assertThat(staticInnerClass.isDeprecated()).isTrue();
    JavaSymbol.TypeJavaSymbol innerClass = (JavaSymbol.TypeJavaSymbol) deprecatedClass.members().lookup("InnerClass").get(0);
    assertThat(innerClass.isDeprecated()).isTrue();
  }

  @Test
  public void complete_flags_for_inner_class() throws Exception {
    JavaSymbol.TypeJavaSymbol classSymbol = bytecodeCompleter.getClassSymbol("org.sonar.java.resolve.targets.ProtectedInnerClassChild");
    JavaSymbol.MethodJavaSymbol foo = (JavaSymbol.MethodJavaSymbol) classSymbol.members().lookup("foo").get(0);
    JavaSymbol.TypeJavaSymbol innerClassRef = foo.getReturnType();
    assertThat(innerClassRef.isPrivate()).isFalse();
    assertThat(innerClassRef.isPublic()).isFalse();
    assertThat(innerClassRef.isPackageVisibility()).isFalse();
    assertThat(innerClassRef.isDeprecated()).isTrue();
  }

  @Test
  public void complete_flags_for_varargs_methods() throws Exception {
    JavaSymbol.TypeJavaSymbol classSymbol = bytecodeCompleter.getClassSymbol("org.sonar.java.resolve.targets.ProtectedInnerClassChild");
    JavaSymbol.MethodJavaSymbol foo = (JavaSymbol.MethodJavaSymbol) classSymbol.members().lookup("foo").get(0);
    assertThat((foo.flags & Flags.VARARGS) != 0).isTrue();
  }

  @Test
  public void annotationOnSymbols() throws Exception {
    JavaSymbol.TypeJavaSymbol classSymbol = bytecodeCompleter.getClassSymbol("org.sonar.java.resolve.targets.AnnotationSymbolMethod");
    assertThat(classSymbol.isPublic()).isTrue();
    SymbolMetadataResolve metadata = classSymbol.metadata();
    assertThat(metadata.annotations()).hasSize(3);
    assertThat(metadata.valuesForAnnotation("org.sonar.java.resolve.targets.Dummy")).isNull();
    assertThat(metadata.valuesForAnnotation("org.sonar.java.resolve.targets.ClassAnnotation")).isEmpty();
    assertThat(metadata.valuesForAnnotation("org.sonar.java.resolve.targets.RuntimeAnnotation1")).hasSize(1);
    assertThat(metadata.valuesForAnnotation("org.sonar.java.resolve.targets.RuntimeAnnotation1").iterator().next().value()).isEqualTo("plopfoo");

    assertThat(metadata.valuesForAnnotation("org.sonar.java.resolve.targets.RuntimeAnnotation2")).hasSize(2);
    Iterator<SymbolMetadata.AnnotationValue> iterator = metadata.valuesForAnnotation("org.sonar.java.resolve.targets.RuntimeAnnotation2").iterator();
    Object value = iterator.next().value();
    assertAnnotationValue(value);
    value = iterator.next().value();
    assertAnnotationValue(value);

  }

  private void assertAnnotationValue(Object value) {
    if (value instanceof JavaSymbol.VariableJavaSymbol) {
      JavaSymbol.VariableJavaSymbol var = (JavaSymbol.VariableJavaSymbol) value;
      assertThat(var.getName()).isEqualTo("ONE");
      assertThat(var.type.is("org.sonar.java.resolve.targets.MyEnum")).isTrue();
      return;
    } else if (value instanceof Object[]) {
      Object[] array = (Object[]) value;
      assertThat(array).hasSize(4);
      assertThat(array).contains("one", "two", "three", "four");
      return;
    }
    fail("value is not array nor variableSymbol");
  }


  @Test
  public void type_parameters_should_be_read_from_bytecode() {
    JavaSymbol.TypeJavaSymbol typeParametersSymbol = bytecodeCompleter.getClassSymbol("org.sonar.java.resolve.targets.TypeParameters");
    typeParametersSymbol.complete();
    assertThat(typeParametersSymbol.typeParameters).isNotNull();
    assertThat(typeParametersSymbol.typeParameters.scopeSymbols()).hasSize(2);
    assertThat(typeParametersSymbol.typeVariableTypes).hasSize(2);

    TypeVariableJavaType TtypeVariableType = typeParametersSymbol.typeVariableTypes.get(0);
    assertThat(TtypeVariableType.erasure().getSymbol().getName()).isEqualTo("Object");
    assertThat(typeParametersSymbol.typeVariableTypes.get(1).erasure().getSymbol().getName()).isEqualTo("CharSequence");

    assertThat(typeParametersSymbol.getSuperclass()).isInstanceOf(ParametrizedTypeJavaType.class);
    assertThat(((ParametrizedTypeJavaType) typeParametersSymbol.getSuperclass()).typeSubstitution.typeVariables()).hasSize(1);
    TypeVariableJavaType keyTypeVariable = ((ParametrizedTypeJavaType) typeParametersSymbol.getSuperclass()).typeSubstitution.typeVariables().iterator().next();
    assertThat(keyTypeVariable.symbol.getName()).isEqualTo("S");
    JavaType actual = ((ParametrizedTypeJavaType) typeParametersSymbol.getSuperclass()).typeSubstitution.substitutedType(keyTypeVariable);
    assertThat(actual).isInstanceOf(ParametrizedTypeJavaType.class);
    assertThat(((ParametrizedTypeJavaType) actual).typeSubstitution.typeVariables()).hasSize(1);

    assertThat(typeParametersSymbol.getInterfaces()).hasSize(2);
    assertThat(typeParametersSymbol.getInterfaces().get(0)).isInstanceOf(ParametrizedTypeJavaType.class);

    JavaSymbol.MethodJavaSymbol funMethod = (JavaSymbol.MethodJavaSymbol) typeParametersSymbol.members().lookup("fun").get(0);
    assertThat(funMethod.getReturnType().type).isSameAs(TtypeVariableType);
    assertThat(funMethod.parameterTypes().get(0)).isSameAs(TtypeVariableType);

    JavaSymbol.MethodJavaSymbol fooMethod = (JavaSymbol.MethodJavaSymbol) typeParametersSymbol.members().lookup("foo").get(0);
    TypeVariableJavaType WtypeVariableType = fooMethod.typeVariableTypes.get(0);
    assertThat(fooMethod.parameterTypes().get(0).isArray()).isTrue();
    assertThat(((ArrayJavaType)fooMethod.parameterTypes().get(0)).elementType()).isSameAs(WtypeVariableType);
    JavaType resultType = ((MethodJavaType) fooMethod.type).resultType;
    assertThat(resultType).isInstanceOf(ParametrizedTypeJavaType.class);
    ParametrizedTypeJavaType actualResultType = (ParametrizedTypeJavaType) resultType;
    assertThat(actualResultType.typeSubstitution.typeVariables()).hasSize(1);
    assertThat(actualResultType.typeSubstitution.substitutedTypes().iterator().next()).isSameAs(WtypeVariableType);

    //primitive types
    assertThat(fooMethod.parameterTypes().get(1).isPrimitive(org.sonar.plugins.java.api.semantic.Type.Primitives.INT)).isTrue();
    assertThat(fooMethod.parameterTypes().get(2).isPrimitive(org.sonar.plugins.java.api.semantic.Type.Primitives.LONG)).isTrue();

    //read field.
    JavaSymbol.VariableJavaSymbol field = (JavaSymbol.VariableJavaSymbol) typeParametersSymbol.members().lookup("field").get(0);
    assertThat(field.type).isInstanceOf(TypeVariableJavaType.class);
    assertThat(field.type).isSameAs(TtypeVariableType);
  }

  @Test
  public void type_parameters_in_inner_class() {
    JavaSymbol.TypeJavaSymbol innerClass = bytecodeCompleter.getClassSymbol("org.sonar.java.resolve.targets.ParametrizedExtend$InnerClass");
    innerClass.complete();
    JavaSymbol.MethodJavaSymbol symbol = (JavaSymbol.MethodJavaSymbol) innerClass.members().lookup("innerMethod").get(0);
    assertThat(symbol.getReturnType().type).isInstanceOf(TypeVariableJavaType.class);
    assertThat(symbol.getReturnType().getName()).isEqualTo("S");
  }

  @Test
  public void annotations_on_members() {
    JavaSymbol.TypeJavaSymbol clazz = bytecodeCompleter.getClassSymbol("org.sonar.java.resolve.targets.AnnotationsOnMembers");
    JavaSymbol.VariableJavaSymbol field = (JavaSymbol.VariableJavaSymbol) clazz.members().lookup("field").get(0);
    JavaSymbol.MethodJavaSymbol method = (JavaSymbol.MethodJavaSymbol) clazz.members().lookup("method").get(0);
    JavaSymbol.VariableJavaSymbol parameter = (JavaSymbol.VariableJavaSymbol) method.getParameters().scopeSymbols().get(0);
    assertThat(field.metadata().valuesForAnnotation("javax.annotation.Nullable")).isNotNull();
    assertThat(field.metadata().isAnnotatedWith("javax.annotation.Nullable")).isTrue();
    assertThat(method.metadata().valuesForAnnotation("javax.annotation.CheckForNull")).isNotNull();
    assertThat(parameter.metadata().valuesForAnnotation("javax.annotation.Nullable")).isNotNull();

  }

  @Test
  public void annotated_enum_constructor() {
    //Test to handle difference between signature and descriptor for enum:
    //see : https://bugs.openjdk.java.net/browse/JDK-8071444 and https://bugs.openjdk.java.net/browse/JDK-8024694
    Symbol.TypeSymbol clazz = bytecodeCompleter.getClassSymbol("org.sonar.java.resolve.targets.AnnotatedEnumConstructor");
    JavaSymbol.MethodJavaSymbol constructor = (JavaSymbol.MethodJavaSymbol) clazz.lookupSymbols("<init>").iterator().next();
    assertThat(constructor.getParameters().scopeSymbols()).hasSize(1);
    for (JavaSymbol arg : constructor.getParameters().scopeSymbols()) {
      assertThat(arg.metadata().annotations()).hasSize(1);
      assertThat(arg.metadata().annotations().get(0).symbol().type().is("javax.annotation.Nullable")).isTrue();
    }
  }

  @Test
  public void wildcards_type_equality() {
    JavaSymbol.TypeJavaSymbol clazz = bytecodeCompleter.getClassSymbol("org.sonar.java.resolve.targets.Wildcards");

    assertTypeAreTheSame(clazz, "equalityUnboundedWildcard1", "equalityUnboundedWildcard2");
    assertTypeAreTheSame(clazz, "equalityExtendsWildcard1", "equalityExtendsWildcard2");
    assertTypeAreTheSame(clazz, "equalitySuperWildcard1", "equalitySuperWildcard2");

    assertTypeAreNotTheSame(clazz, "equalityUnboundedWildcard1", "equalityExtendsWildcard1");
    assertTypeAreNotTheSame(clazz, "equalityUnboundedWildcard1", "equalitySuperWildcard1");
    assertTypeAreNotTheSame(clazz, "equalityExtendsWildcard1", "equalitySuperWildcard1");
  }

  private static void assertTypeAreTheSame(TypeJavaSymbol clazz, String varName1, String varName2) {
    JavaSymbol.VariableJavaSymbol var1 = getVariable(clazz, varName1);
    JavaSymbol.VariableJavaSymbol var2 = getVariable(clazz, varName2);
    assertThat(var1.type).isSameAs(var2.type);
  }

  private static void assertTypeAreNotTheSame(TypeJavaSymbol clazz, String varName1, String varName2) {
    JavaSymbol.VariableJavaSymbol var1 = getVariable(clazz, varName1);
    JavaSymbol.VariableJavaSymbol var2 = getVariable(clazz, varName2);
    assertThat(var1.type).isNotSameAs(var2.type);
  }

  private static JavaSymbol.VariableJavaSymbol getVariable(JavaSymbol.TypeJavaSymbol clazz, String name) {
    return (JavaSymbol.VariableJavaSymbol) clazz.members().lookup(name).get(0);
  }

  @Test
  public void wildcards_in_fields_declaration() {
    JavaSymbol.TypeJavaSymbol clazz = bytecodeCompleter.getClassSymbol("org.sonar.java.resolve.targets.Wildcards");
    JavaSymbol.VariableJavaSymbol unboudedItems = getVariable(clazz, "unboudedItems");
    JavaSymbol.VariableJavaSymbol extendsItems = getVariable(clazz, "extendsItems");
    JavaSymbol.VariableJavaSymbol superItems = getVariable(clazz, "superItems");

    assertThatWildcardIs(unboudedItems, WildCardType.BoundType.UNBOUNDED, "java.lang.Object");
    assertThatWildcardIs(extendsItems, WildCardType.BoundType.EXTENDS, "java.lang.String");
    assertThatWildcardIs(superItems, WildCardType.BoundType.SUPER, "java.lang.Number");
  }

  @Test
  public void wildcards_in_methods_declaration() {
    JavaSymbol.TypeJavaSymbol clazz = bytecodeCompleter.getClassSymbol("org.sonar.java.resolve.targets.Wildcards");
    JavaSymbol.MethodJavaSymbol returnsUnboundedItems = (JavaSymbol.MethodJavaSymbol) clazz.members().lookup("returnsUnboundedItems").get(0);
    JavaSymbol.MethodJavaSymbol returnsExtendsItems = (JavaSymbol.MethodJavaSymbol) clazz.members().lookup("returnsExtendsItems").get(0);
    JavaSymbol.MethodJavaSymbol returnsSuperItems = (JavaSymbol.MethodJavaSymbol) clazz.members().lookup("returnsSuperItems").get(0);

    assertThatWildcardIs(returnsUnboundedItems, WildCardType.BoundType.UNBOUNDED, "java.lang.Object");
    assertThatWildcardIs(returnsExtendsItems, WildCardType.BoundType.EXTENDS, "java.lang.String");
    assertThatWildcardIs(returnsSuperItems, WildCardType.BoundType.SUPER, "java.lang.Number");

    assertThatWildcardInFirstParamIs(returnsUnboundedItems, WildCardType.BoundType.UNBOUNDED, "java.lang.Object");
    assertThatWildcardInFirstParamIs(returnsExtendsItems, WildCardType.BoundType.EXTENDS, "java.lang.String");
    assertThatWildcardInFirstParamIs(returnsSuperItems, WildCardType.BoundType.SUPER, "java.lang.Number");
  }

  @Test
  public void wildcards_in_class_declaration() {
    JavaSymbol.TypeJavaSymbol wildcardUnboundedClass = bytecodeCompleter.getClassSymbol("org.sonar.java.resolve.targets.WildcardUnboundedClass");
    JavaSymbol.TypeJavaSymbol WildcardExtendsClass = bytecodeCompleter.getClassSymbol("org.sonar.java.resolve.targets.WildcardExtendsClass");
    JavaSymbol.TypeJavaSymbol WildcardSuperClass = bytecodeCompleter.getClassSymbol("org.sonar.java.resolve.targets.WildcardSuperClass");

    assertThatWildcardInTypeParamIs(wildcardUnboundedClass, WildCardType.BoundType.UNBOUNDED, "java.lang.Object");
    assertThatWildcardInTypeParamIs(WildcardExtendsClass, WildCardType.BoundType.EXTENDS, "java.lang.String");
    assertThatWildcardInTypeParamIs(WildcardSuperClass, WildCardType.BoundType.SUPER, "java.lang.Number");
  }

  private static void assertThatWildcardInTypeParamIs(JavaSymbol.TypeJavaSymbol symbol, WildCardType.BoundType wildcard, String bound) {
    JavaType javaType = ((TypeVariableJavaType) symbol.typeParameters().lookup("X").iterator().next().getType()).bounds.get(0);
    assertThatWildcardIs(javaType, wildcard, bound);
  }

  private static void assertThatWildcardInFirstParamIs(JavaSymbol.MethodJavaSymbol symbol, WildCardType.BoundType wildcard, String bound) {
    assertThatWildcardIs(symbol.parameterTypes().get(0), wildcard, bound);
  }

  private static void assertThatWildcardIs(JavaSymbol.MethodJavaSymbol symbol, WildCardType.BoundType wildcard, String bound) {
    assertThatWildcardIs(((MethodJavaType) symbol.type).resultType, wildcard, bound);
  }

  private static void assertThatWildcardIs(JavaSymbol.VariableJavaSymbol symbol, WildCardType.BoundType wildcard, String bound) {
    assertThatWildcardIs(symbol.type, wildcard, bound);
  }

  private static void assertThatWildcardIs(Type type, WildCardType.BoundType wildcard, String bound) {
    assertThat(type).isInstanceOf(ParametrizedTypeJavaType.class);
    
    ParametrizedTypeJavaType ptjt = (ParametrizedTypeJavaType) type;
    JavaType substitutedType = ptjt.typeSubstitution.substitutedTypes().get(0);
    assertThat(substitutedType).isInstanceOf(WildCardType.class);
    
    WildCardType wildcardType = (WildCardType) substitutedType;
    assertThat(wildcardType.boundType).isEqualTo(wildcard);
    assertThat(wildcardType.bound.is(bound)).isTrue();
  }

  @Test
  public void class_not_found_should_have_unknown_super_type_and_no_interfaces() {
    Symbol.TypeSymbol clazz = bytecodeCompleter.getClassSymbol("org.sonar.java.resolve.targets.UnknownClass");
    assertThat(clazz.type()).isNotNull();
    Type superClass = clazz.superClass();
    assertThat(superClass).isNotNull();
    assertThat(superClass).isSameAs(Symbols.unknownType);
    List<Type> interfaces = clazz.interfaces();
    assertThat(interfaces).isNotNull();
    assertThat(interfaces).isEmpty();
  }

  @Test
  public void forward_type_parameter_in_methods() throws Exception {
    Symbol.TypeSymbol clazz = bytecodeCompleter.getClassSymbol("org.sonar.java.resolve.targets.ForwardParameterInMethod");
    assertThat(clazz.type()).isNotNull();
    Collection<Symbol> symbols = clazz.lookupSymbols("bar");
    assertThat(symbols).hasSize(1);
    Symbol method = symbols.iterator().next();
    Collection<JavaSymbol> typeParameters = ((JavaSymbol.MethodJavaSymbol) method).typeParameters().scopeSymbols();
    assertThat(typeParameters).hasSize(2);
    JavaSymbol xSymbol = ((JavaSymbol.MethodJavaSymbol) method).typeParameters().lookup("X").iterator().next();
    JavaSymbol ySymbol = ((JavaSymbol.MethodJavaSymbol) method).typeParameters().lookup("Y").iterator().next();
    assertThat(((TypeVariableJavaType) xSymbol.type).bounds).hasSize(1);
    JavaType bound = ((TypeVariableJavaType) xSymbol.type).bounds.get(0);
    assertThat(((ParametrizedTypeJavaType)bound).typeParameters()).hasSize(1);
    assertThat(((ParametrizedTypeJavaType)bound).substitution(((ParametrizedTypeJavaType)bound).typeParameters().get(0))).isSameAs(ySymbol.type);
  }

  @Test
  public void forward_type_parameter_in_classes() throws Exception {
    Symbol.TypeSymbol clazz = bytecodeCompleter.getClassSymbol("org.sonar.java.resolve.targets.ForwardParameterInClass");
    assertThat(clazz.type()).isNotNull();
    Collection<Symbol> symbols = clazz.lookupSymbols("bar");
    assertThat(symbols).hasSize(1);
    Collection<JavaSymbol> typeParameters = ((JavaSymbol.TypeJavaSymbol) clazz).typeParameters().scopeSymbols();
    assertThat(typeParameters).hasSize(2);
    JavaSymbol xSymbol = ((JavaSymbol.TypeJavaSymbol) clazz).typeParameters().lookup("X").iterator().next();
    JavaSymbol ySymbol = ((JavaSymbol.TypeJavaSymbol) clazz).typeParameters().lookup("Y").iterator().next();
    assertThat(((TypeVariableJavaType) xSymbol.type).bounds).hasSize(1);
    JavaType bound = ((TypeVariableJavaType) xSymbol.type).bounds.get(0);
    assertThat(((ParametrizedTypeJavaType)bound).typeParameters()).hasSize(1);
    assertThat(((ParametrizedTypeJavaType)bound).substitution(((ParametrizedTypeJavaType)bound).typeParameters().get(0))).isSameAs(ySymbol.type);

  }
}
