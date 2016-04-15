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
import org.sonar.plugins.java.api.semantic.Type;

import javax.annotation.Nullable;
import java.util.List;

public class ParametrizedTypeJavaType extends ClassJavaType {

  final TypeSubstitution typeSubstitution;
  final JavaType rawType;

  ParametrizedTypeJavaType(JavaSymbol.TypeJavaSymbol symbol, TypeSubstitution typeSubstitution) {
    super(symbol);
    this.rawType = symbol.getType();
    this.typeSubstitution = typeSubstitution;
  }


  @Override
  public JavaType erasure() {
    return rawType.erasure();
  }

  @Nullable
  public JavaType substitution(TypeVariableJavaType typeVariableType) {
    JavaType result = null;
    if (typeSubstitution != null) {
      result = typeSubstitution.substitutedType(typeVariableType);
    }
    return result;
  }

  public List<TypeVariableJavaType> typeParameters() {
    if (typeSubstitution != null) {
      return typeSubstitution.typeVariables();
    }
    return Lists.newArrayList();
  }

  @Override
  public boolean isSubtypeOf(Type superType) {
    if (((JavaType) superType).isTagged(TYPEVAR)) {
      return false;
    }
    if (erasure().isSubtypeOf(superType.erasure())) {
      boolean superTypeIsParametrizedJavaType = superType instanceof org.sonar.java.resolve.ParametrizedTypeJavaType;
      if (superTypeIsParametrizedJavaType) {
        return checkSubstitutedTypesCompatibility((org.sonar.java.resolve.ParametrizedTypeJavaType) superType);
      }
      return !superTypeIsParametrizedJavaType;
    }
    if (((JavaType) superType).isTagged(WILDCARD)) {
      return ((WildCardType) superType).isSubtypeOfBound(this);
    }
    return false;
  }

  private boolean checkSubstitutedTypesCompatibility(org.sonar.java.resolve.ParametrizedTypeJavaType superType) {
    List<JavaType> myTypes = typeSubstitution.substitutedTypes();
    List<JavaType> itsTypes = superType.typeSubstitution.substitutedTypes();
    if (itsTypes.size() != myTypes.size()) {
      return false;
    }
    for (int i = 0; i < myTypes.size(); i++) {
      JavaType myType = myTypes.get(i);
      JavaType itsType = itsTypes.get(i);
      if (itsType.isTagged(WILDCARD)) {
        if (!myType.isSubtypeOf(itsType)) {
          return false;
        }
      } else if (!myType.equals(itsType)) {
        return false;
      }
    }
    return true;
  }
}
