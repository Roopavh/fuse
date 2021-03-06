/**
 * Copyright (C) FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.fabric.service;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.karaf.admin.management.AdminServiceMBean;
import org.apache.zookeeper.KeeperException;
import org.fusesource.fabric.api.Container;
import org.fusesource.fabric.api.ContainerProvider;
import org.fusesource.fabric.api.CreateChildContainerMetadata;
import org.fusesource.fabric.api.CreateChildContainerOptions;
import org.fusesource.fabric.api.DataStore;
import org.fusesource.fabric.api.FabricService;
import org.fusesource.fabric.api.PortService;
import org.fusesource.fabric.api.Profile;
import org.fusesource.fabric.internal.ContainerImpl;
import org.fusesource.fabric.utils.AuthenticationUtils;
import org.fusesource.fabric.utils.Constants;
import org.fusesource.fabric.utils.Ports;
import org.fusesource.fabric.zookeeper.ZkDefs;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.fusesource.fabric.utils.Ports.mapPortToRange;

@Component(name = "org.fusesource.fabric.container.provider.child",
           description = "Child Container Provider", immediate = true)
@Service(ContainerProvider.class)
public class ChildContainerProvider implements ContainerProvider<CreateChildContainerOptions, CreateChildContainerMetadata> {

    private static final String SCHEME = "child";

    @Reference(cardinality = org.apache.felix.scr.annotations.ReferenceCardinality.MANDATORY_UNARY)
    private FabricService service;


    @Override
    public Set<CreateChildContainerMetadata> create(final CreateChildContainerOptions options) throws Exception {
        final Set<CreateChildContainerMetadata> result = new LinkedHashSet<CreateChildContainerMetadata>();
        final String parentName = options.getParent();
        final Container parent = service.getContainer(parentName);
        ContainerTemplate containerTemplate =  new ContainerTemplate(parent, options.getJmxUser(), options.getJmxPassword(), false);

        containerTemplate.execute(new ContainerTemplate.AdminServiceCallback<Object>() {
            public Object doWithAdminService(AdminServiceMBean adminService) throws Exception {
                StringBuilder jvmOptsBuilder = new StringBuilder();

                jvmOptsBuilder.append("-server -Dcom.sun.management.jmxremote")
                        .append(options.getZookeeperUrl() != null ? " -Dzookeeper.url=\"" + options.getZookeeperUrl() + "\"" : "")
                        .append(options.getZookeeperPassword() != null ? " -Dzookeeper.password=\"" + options.getZookeeperPassword() + "\"" : "");

                if (options.getJvmOpts() == null || !options.getJvmOpts().contains("-Xmx")) {
                    jvmOptsBuilder.append(" -Xmx512m");
                }
                if (options.isEnsembleServer()) {
                    jvmOptsBuilder.append(" ").append(Constants.ENSEMBLE_SERVER_CONTAINER);
                }

                if (options.getJvmOpts() != null && !options.getJvmOpts().isEmpty()) {
                    jvmOptsBuilder.append(" ").append(options.getJvmOpts());
                }

                if (options.getJvmOpts() != null && !options.getJvmOpts().contains("-XX:+UnlockDiagnosticVMOptions -XX:+UnsyncloadClass")) {
                    jvmOptsBuilder.append(" -XX:+UnlockDiagnosticVMOptions -XX:+UnsyncloadClass");
                }

                if (options.getBindAddress() != null && !options.getBindAddress().isEmpty()) {
                    jvmOptsBuilder.append(" -D" + ZkDefs.BIND_ADDRESS + "=" + options.getBindAddress());
                }

                if (options.getResolver() != null && !options.getResolver().isEmpty()) {
                    jvmOptsBuilder.append(" -D" + ZkDefs.LOCAL_RESOLVER_PROPERTY + "=" + options.getResolver());
                }

                if (options.getManualIp() != null && !options.getManualIp().isEmpty()) {
                    jvmOptsBuilder.append(" -D" + ZkDefs.MANUAL_IP + "=" + options.getManualIp());
                }

                Profile defaultProfile = parent.getVersion().getProfile("default");
                String featuresUrls = collectionAsString(defaultProfile.getRepositories());
                Set<String> features = new LinkedHashSet<String>();
                //TODO: This is a temporary fix till we address the url handlers in the deployment agent.
                features.add("war");
                features.add("fabric-agent");
                features.add("fabric-git");
                //features.addAll(defaultProfile.getFeatures());
                String originalName = options.getName();

                PortService portService = service.getPortService();
                Set<Integer> usedPorts = portService.findUsedPortByHost(parent);

                int number = Math.max(options.getNumber(), 1);
                for (int i = 1; i <= number; i++) {
                    String containerName;
                    if (options.getNumber() >= 1) {
                        containerName = originalName + i;
                    } else {
                        containerName = originalName;
                    }
                    CreateChildContainerMetadata metadata = new CreateChildContainerMetadata();

                    metadata.setCreateOptions(options);
                    metadata.setContainerName(containerName);
                    int minimumPort = parent.getMinimumPort();
                    int maximumPort = parent.getMaximumPort();

                    service.getDataStore().setContainerAttribute(containerName, DataStore.ContainerAttribute.PortMin, String.valueOf(minimumPort));
                    service.getDataStore().setContainerAttribute(containerName, DataStore.ContainerAttribute.PortMax, String.valueOf(maximumPort));
                    inheritAddresses(service, parentName, containerName, options);

                    //We are creating a container instance, just for the needs of port registration.
                    Container child = new ContainerImpl(parent, containerName, service) {
                        @Override
                        public String getIp() {
                            return parent.getIp();
                        }
                    };

                    int sshFrom = mapPortToRange(Ports.DEFAULT_KARAF_SSH_PORT , minimumPort, maximumPort);
                    int sshTo = mapPortToRange(Ports.DEFAULT_KARAF_SSH_PORT + 100 , minimumPort, maximumPort);
                    int sshPort = portService.registerPort(child, "org.apache.karaf.shell", "sshPort", sshFrom, sshTo, usedPorts);


                    int httpFrom = mapPortToRange(Ports.DEFAULT_HTTP_PORT , minimumPort, maximumPort);
                    int httpTo = mapPortToRange(Ports.DEFAULT_HTTP_PORT + 100 , minimumPort, maximumPort);
                    portService.registerPort(child, "org.ops4j.pax.web", "org.osgi.service.http.port", httpFrom, httpTo, usedPorts);

                    int rmiServerFrom = mapPortToRange(Ports.DEFAULT_RMI_SERVER_PORT , minimumPort, maximumPort);
                    int rmiServerTo = mapPortToRange(Ports.DEFAULT_RMI_SERVER_PORT + 100 , minimumPort, maximumPort);
                    int rmiServerPort = portService.registerPort(child, "org.apache.karaf.management", "rmiServerPort", rmiServerFrom, rmiServerTo, usedPorts);

                    int rmiRegistryFrom = mapPortToRange(Ports.DEFAULT_RMI_REGISTRY_PORT , minimumPort, maximumPort);
                    int rmiRegistryTo = mapPortToRange(Ports.DEFAULT_RMI_REGISTRY_PORT + 100 , minimumPort, maximumPort);
                    int rmiRegistryPort = portService.registerPort(child, "org.apache.karaf.management", "rmiRegistryPort", rmiRegistryFrom, rmiRegistryTo, usedPorts);


                    try {
                        adminService.createInstance(containerName,
                                sshPort,
                                rmiRegistryPort,
                                rmiServerPort, null, jvmOptsBuilder.toString(), collectionAsString(features), featuresUrls);
                        adminService.startInstance(containerName, null);
                    } catch (Throwable t) {
                        metadata.setFailure(t);
                    }
                    result.add(metadata);
                }
                return null;
            }
        });
        return result;
    }

    @Override
    public void start(final Container container) {
        getContainerTemplateForChild(container).execute(new ContainerTemplate.AdminServiceCallback<Object>() {
            public Object doWithAdminService(AdminServiceMBean adminService) throws Exception {
                adminService.startInstance(container.getId(), null);
                return null;
            }
        });
    }

    @Override
    public void stop(final Container container) {
        getContainerTemplateForChild(container).execute(new ContainerTemplate.AdminServiceCallback<Object>() {
            public Object doWithAdminService(AdminServiceMBean adminService) throws Exception {
                adminService.stopInstance(container.getId());
                return null;
            }
        });
    }

    @Override
    public void destroy(final Container container) {
        getContainerTemplateForChild(container).execute(new ContainerTemplate.AdminServiceCallback<Object>() {
            public Object doWithAdminService(AdminServiceMBean adminService) throws Exception {
                try {
                    adminService.stopInstance(container.getId());
                } catch (Exception e) {
                    // Ignore if the container is stopped
                    if (container.isAlive()) {
                        throw e;
                    }
                }
                adminService.destroyInstance(container.getId());
                return null;
            }
        });
    }

    @Override
    public String getScheme() {
        return SCHEME;
    }

    @Override
    public Class<CreateChildContainerOptions> getOptionsType() {
        return CreateChildContainerOptions.class;
    }

    @Override
    public Class<CreateChildContainerMetadata> getMetadataType() {
        return CreateChildContainerMetadata.class;
    }

    /**
     * Returns the {@link ContainerTemplate} of the parent of the specified child {@link Container}.
     *
     * @param container
     * @return
     */
    protected ContainerTemplate getContainerTemplateForChild(Container container) {
        CreateChildContainerOptions options = (CreateChildContainerOptions) container.getMetadata().getCreateOptions();

        String username = AuthenticationUtils.retrieveJaasUser();
        String password = AuthenticationUtils.retrieveJaasPassword();

        if (username != null && password != null) {
            options = (CreateChildContainerOptions) options.updateCredentials(username, password);
        }

        return new ContainerTemplate(container.getParent(), options.getJmxUser(), options.getJmxPassword(), false);
    }

    /**
     * Links child container resolver and addresses to its parents resolver and addresses.
     *
     * @param service
     * @param parent
     * @param name
     * @param options
     * @throws KeeperException
     * @throws InterruptedException
     */
    private void inheritAddresses(FabricService service, String parent, String name, CreateChildContainerOptions options) throws Exception {
        if (options.getManualIp() != null) {
            service.getDataStore().setContainerAttribute(name, DataStore.ContainerAttribute.ManualIp, options.getManualIp());
        } else {
            service.getDataStore().setContainerAttribute(name, DataStore.ContainerAttribute.ManualIp, "${zk:" + parent + "/manulaip}");
        }

        //Link to the addresses from the parent container.
        service.getDataStore().setContainerAttribute(name, DataStore.ContainerAttribute.LocalHostName, "${zk:" + parent + "/localhostname}");
        service.getDataStore().setContainerAttribute(name, DataStore.ContainerAttribute.LocalIp, "${zk:" + parent + "/localip}");
        service.getDataStore().setContainerAttribute(name, DataStore.ContainerAttribute.PublicIp, "${zk:" + parent + "/publicip}");

        if (options.getResolver() != null) {
            service.getDataStore().setContainerAttribute(name, DataStore.ContainerAttribute.Resolver, options.getResolver());
        } else {
            service.getDataStore().setContainerAttribute(name, DataStore.ContainerAttribute.Resolver, "${zk:" + parent + "/resolver}");
        }

        if (options.getBindAddress() != null) {
            service.getDataStore().setContainerAttribute(name, DataStore.ContainerAttribute.BindAddress, options.getBindAddress());
        } else {
            service.getDataStore().setContainerAttribute(name, DataStore.ContainerAttribute.BindAddress,  "${zk:" + parent + "/bindaddress}");
        }

        service.getDataStore().setContainerAttribute(name, DataStore.ContainerAttribute.Ip, "${zk:" + parent + "/resolver}");
    }

    private static String collectionAsString(Collection<String> value) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        if (value != null) {
            for (String el : value) {
                if (first) {
                    first = false;
                } else {
                    sb.append(",");
                }
                sb.append(el);
            }
        }
        return sb.toString();
    }
}
