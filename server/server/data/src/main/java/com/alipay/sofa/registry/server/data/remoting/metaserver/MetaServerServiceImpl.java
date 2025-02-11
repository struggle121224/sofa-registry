/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.sofa.registry.server.data.remoting.metaserver;

import com.alipay.sofa.registry.common.model.Tuple;
import com.alipay.sofa.registry.common.model.metaserver.inter.heartbeat.BaseHeartBeatResponse;
import com.alipay.sofa.registry.common.model.metaserver.inter.heartbeat.HeartbeatRequest;
import com.alipay.sofa.registry.common.model.metaserver.nodes.DataNode;
import com.alipay.sofa.registry.common.model.slot.BaseSlotStatus;
import com.alipay.sofa.registry.common.model.slot.SlotConfig;
import com.alipay.sofa.registry.common.model.slot.SlotTable;
import com.alipay.sofa.registry.common.model.store.URL;
import com.alipay.sofa.registry.server.data.bootstrap.DataServerConfig;
import com.alipay.sofa.registry.server.data.remoting.DataNodeExchanger;
import com.alipay.sofa.registry.server.data.remoting.SessionNodeExchanger;
import com.alipay.sofa.registry.server.data.slot.SlotManager;
import com.alipay.sofa.registry.server.shared.env.ServerEnv;
import com.alipay.sofa.registry.server.shared.meta.AbstractMetaServerService;
import com.alipay.sofa.registry.server.shared.slot.SlotTableRecorder;
import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author qian.lqlq
 * @version $Id: MetaServiceImpl.java, v 0.1 2018－03－07 20:41 qian.lqlq Exp $
 */
public class MetaServerServiceImpl extends AbstractMetaServerService<BaseHeartBeatResponse>
    implements SlotTableRecorder {

  @Autowired private SlotManager slotManager;

  @Autowired private DataNodeExchanger dataNodeExchanger;

  @Autowired private SessionNodeExchanger sessionNodeExchanger;

  @Autowired private DataServerConfig dataServerConfig;

  private volatile SlotTable currentSlotTable;

  @Override
  protected long getCurrentSlotTableEpoch() {
    return slotManager.getSlotTableEpoch();
  }

  @Override
  public int getRenewIntervalSecs() {
    return dataServerConfig.getSchedulerHeartbeatIntervalSecs();
  }

  @Override
  protected void handleRenewResult(BaseHeartBeatResponse result) {
    // the data/session list has updated in renewNode
    Set<String> dataServerList = getDataServerList();
    if (!CollectionUtils.isEmpty(dataServerList)) {
      dataNodeExchanger.setServerIps(dataServerList);
      dataNodeExchanger.notifyConnectServerAsync();
    }
    Set<String> sessionServerList = getSessionServerList();
    if (!CollectionUtils.isEmpty(sessionServerList)) {
      sessionNodeExchanger.setServerIps(sessionServerList);
      sessionNodeExchanger.notifyConnectServerAsync();
    }
    if (result.getSlotTable() != null
        && result.getSlotTable().getEpoch() != SlotTable.INIT.getEpoch()) {
      slotManager.updateSlotTable(result.getSlotTable());
    } else {
      RENEWER_LOGGER.error(
          "[handleRenewResult] slot table is {}",
          result.getSlotTable() == null ? "null" : "SlotTable.INIT");
    }
  }

  @Override
  protected HeartbeatRequest createRequest() {
    Tuple<Long, List<BaseSlotStatus>> tuple = slotManager.getSlotTableEpochAndStatuses();
    final long slotTableEpoch = tuple.o1;
    final List<BaseSlotStatus> slotStatuses = tuple.o2;
    HeartbeatRequest<DataNode> request =
        new HeartbeatRequest<>(
                createNode(),
                slotTableEpoch,
                dataServerConfig.getLocalDataCenter(),
                System.currentTimeMillis(),
                SlotConfig.slotBasicInfo(),
                slotStatuses)
            .setSlotTable(currentSlotTable);
    return request;
  }

  private DataNode createNode() {
    return new DataNode(new URL(ServerEnv.IP), dataServerConfig.getLocalDataCenter());
  }

  @VisibleForTesting
  void setSlotManager(SlotManager slotManager) {
    this.slotManager = slotManager;
  }

  @VisibleForTesting
  void setDataNodeExchanger(DataNodeExchanger dataNodeExchanger) {
    this.dataNodeExchanger = dataNodeExchanger;
  }

  @VisibleForTesting
  void setSessionNodeExchanger(SessionNodeExchanger sessionNodeExchanger) {
    this.sessionNodeExchanger = sessionNodeExchanger;
  }

  @VisibleForTesting
  void setDataServerConfig(DataServerConfig dataServerConfig) {
    this.dataServerConfig = dataServerConfig;
  }

  @Override
  public void record(SlotTable slotTable) {
    currentSlotTable = new SlotTable(slotTable.getEpoch(), slotTable.getSlots());
  }
}
