// Copyright 2014 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.analysis.configuredtargets;

import static net.starlark.java.eval.Module.ofInnermostEnclosingStarlarkFunction;

import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.Allowlist;
import com.google.devtools.build.lib.analysis.FileProvider;
import com.google.devtools.build.lib.analysis.PackageSpecificationProvider;
import com.google.devtools.build.lib.analysis.TargetContext;
import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import com.google.devtools.build.lib.cmdline.BazelModuleContext;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.cmdline.RepositoryName;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.packages.Info;
import com.google.devtools.build.lib.packages.PackageGroup;
import com.google.devtools.build.lib.packages.PackageSpecification.PackageGroupContents;
import com.google.devtools.build.lib.packages.Provider;
import javax.annotation.Nullable;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkThread;

/**
 * Dummy ConfiguredTarget for package groups. Contains no functionality, since package groups are
 * not really first-class Targets.
 */
@Immutable
public class PackageGroupConfiguredTarget extends AbstractConfiguredTarget {
  private final PackageSpecificationProvider packageSpecificationProvider;

  @Override
  public <P extends TransitiveInfoProvider> P getProvider(Class<P> provider) {
    if (provider == FileProvider.class) {
      return provider.cast(FileProvider.EMPTY); // can't fail
    }
    if (provider == PackageSpecificationProvider.class) {
      return provider.cast(packageSpecificationProvider);
    } else {
      return super.getProvider(provider);
    }
  }

  public PackageGroupConfiguredTarget(
      Label label,
      NestedSet<PackageGroupContents> visibility,
      TargetContext targetContext,
      PackageGroup packageGroup) {
    super(label, null, visibility);
    this.packageSpecificationProvider =
        PackageSpecificationProvider.create(targetContext, packageGroup);
  }

  public PackageGroupConfiguredTarget(TargetContext targetContext, PackageGroup packageGroup) {
    this(targetContext.getLabel(), targetContext.getVisibility(), targetContext, packageGroup);
  }

  @Override
  @Nullable
  protected Info rawGetStarlarkProvider(Provider.Key providerKey) {
    if (providerKey.equals(packageSpecificationProvider.getProvider().getKey())) {
      return packageSpecificationProvider;
    }
    return null;
  }

  @Override
  protected Object rawGetStarlarkProvider(String providerKey) {
    return null;
  }

  @StarlarkMethod(
      name = "isAvailableFor",
      documented = false,
      parameters = {
        @Param(
            name = "label",
            allowedTypes = {@ParamType(type = Label.class)})
      },
      useStarlarkThread = true)
  public boolean starlarkMatches(Label label, StarlarkThread starlarkThread) throws EvalException {
    RepositoryName repository =
        BazelModuleContext.of(ofInnermostEnclosingStarlarkFunction(starlarkThread))
            .label()
            .getRepository();
    if (!"@_builtins".equals(repository.getNameWithAt())) {
      throw Starlark.errorf("private API only for use by builtins");
    }
    return Allowlist.isAvailableFor(packageSpecificationProvider.getPackageSpecifications(), label);
  }
}
