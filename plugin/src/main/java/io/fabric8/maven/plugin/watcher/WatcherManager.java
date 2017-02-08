/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.fabric8.maven.plugin.watcher;

import java.util.List;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.util.ClassUtil;
import io.fabric8.maven.core.util.PluginServiceFactory;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * Manager responsible for finding and calling watchers
 *
 * @author nicola
 * @since 06/02/17
 */
public class WatcherManager {

    public static void watch(List<ImageConfiguration> ret, WatcherContext watcherCtx) throws MojoExecutionException {

        PluginServiceFactory<WatcherContext> pluginFactory =
                watcherCtx.isUseProjectClasspath() ?
            new PluginServiceFactory<>(watcherCtx, ClassUtil.createProjectClassLoader(watcherCtx.getProject(), watcherCtx.getLogger())) :
            new PluginServiceFactory<>(watcherCtx);

        boolean isOpenshift = KubernetesHelper.isOpenShift(watcherCtx.getKubernetesClient());
        PlatformMode mode = isOpenshift ? PlatformMode.openshift : PlatformMode.kubernetes;

        List<Watcher> watchers =
            pluginFactory.createServiceObjects("META-INF/fabric8/watcher-default",
                                               "META-INF/fabric8/fabric8-watcher-default",
                                               "META-INF/fabric8/watcher",
                                               "META-INF/fabric8-watcher");
        ProcessorConfig config = watcherCtx.getConfig();
        Logger log = watcherCtx.getLogger();
        List<Watcher> usableWatchers  = config.prepareProcessors(watchers, "watcher");
        log.verbose("Watchers:");
        Watcher chosen = null;
        for (Watcher watcher : usableWatchers) {
            log.verbose(" - %s",watcher.getName());
            if (watcher.isApplicable(ret, mode)) {
                chosen = watcher;
                break;
            }
        }

        if (chosen == null) {
            throw new IllegalStateException("No watchers can be used for the current project");
        }


        log.info("Running watcher %s", chosen.getName());
        chosen.watch(ret, mode);
    }
}
