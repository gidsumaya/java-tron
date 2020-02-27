package org.tron.core.metrics.blockchain;

import com.codahale.metrics.Meter;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.core.ChainBaseManager;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.metrics.MetricsKey;
import org.tron.core.metrics.MetricsService;
import org.tron.core.metrics.net.RateInfo;

@Component
public class BlockChainMetricManager {

  private static Map<String, WitnessInfo> witnessVersion = new HashMap<>();

  private static int currentVersion;

  @Autowired
  private ChainBaseManager chainBaseManager;

  @Autowired
  private MetricsService metricsService;


  public BlockChainInfo getBlockchainInfo() {
    return new BlockChainInfo();
  }

  public void applyBlcok(BlockCapsule block) {
    String witnessAddress = block.getWitnessAddress().toStringUtf8();
    int version = block.getInstance().getBlockHeader().getRawData().getVersion();
    currentVersion = Math.max(currentVersion, version);
    if (witnessVersion.containsKey(witnessAddress)
        && witnessVersion.get(witnessAddress).getVersion() != version) {
      // just update version
      WitnessInfo witness = witnessVersion.get(witnessAddress);
      witness.setVersion(version);
      witnessVersion.put(witnessAddress, witness);
    } else {
      List<WitnessCapsule> allWitness = chainBaseManager.getWitnessStore().getAllWitnesses();
      for (WitnessCapsule it : allWitness) {  // add new witness
        if (it.getAddress().toStringUtf8().equals(witnessAddress)) {
          WitnessInfo witness = new WitnessInfo(witnessAddress, version);
          witnessVersion.put(it.getAddress().toStringUtf8(), witness);
        }
      }
    }
  }

  public List<WitnessInfo> getNoUpgradedSRList() {
    List<WitnessInfo> noUpgradedWitness = new ArrayList<>();

    List<ByteString> address = chainBaseManager.getWitnessScheduleStore().getActiveWitnesses();
    for (ByteString it : address) {
      if (witnessVersion.containsKey(it.toStringUtf8())
          && witnessVersion.get(it.toStringUtf8()).getVersion() != currentVersion) {
        WitnessInfo witness = witnessVersion.get(it.toStringUtf8());
        noUpgradedWitness.add(witness);
      }
    }
    return noUpgradedWitness;
  }

  public RateInfo getBlockProcessTime() {
    RateInfo blockProcessTime = new RateInfo();

    blockProcessTime.setMeanRate(getAvgBlockProcessTimeByGap(0));
    blockProcessTime.setOneMinuteRate(getAvgBlockProcessTimeByGap(1));
    blockProcessTime.setFiveMinuteRate(getAvgBlockProcessTimeByGap(5));
    blockProcessTime.setFifteenMinuteRate(getAvgBlockProcessTimeByGap(15));
    return blockProcessTime;
  }

  public RateInfo getTransactionRate() {
    Meter transactionRate = metricsService.getMeter(MetricsKey.BLOCKCHAIN_TPS);
    RateInfo tpsInfo = new RateInfo();
    tpsInfo.setMeanRate(transactionRate.getMeanRate());
    tpsInfo.setOneMinuteRate(transactionRate.getOneMinuteRate());
    tpsInfo.setFiveMinuteRate(transactionRate.getFiveMinuteRate());
    tpsInfo.setFifteenMinuteRate(transactionRate.getFifteenMinuteRate());
    return tpsInfo;
  }

  public List<WitnessInfo> getNoUpgradedSR() {
    List<WitnessInfo> witnesses = new ArrayList<>();
    for (WitnessInfo it : getNoUpgradedSRList()) {
      WitnessInfo noUpgradeSR = new WitnessInfo();
      noUpgradeSR.setAddress(it.getAddress());
      noUpgradeSR.setVersion(it.getVersion());
      witnesses.add(noUpgradeSR);
    }
    return witnesses;
  }


  // gap: 1 minute, 5 minute, 15 minute, 0: avg for total block and time
  private double getAvgBlockProcessTimeByGap(int gap) {
    Meter meterBlockProcessTime =
        metricsService.getMeter(MetricsKey.BLOCKCHAIN_BLOCKPROCESS_TIME);
    Meter meterBlockTxCount = metricsService.getMeter(MetricsKey.BLOCKCHAIN_BLOCK_COUNT);
    if (meterBlockTxCount.getCount() == 0) {
      return 0;
    }
    switch (gap) {
      case 0:
        return (meterBlockProcessTime.getCount() / (double) meterBlockTxCount.getCount());
      case 1:
        int gapMinuteTimeBlock =
            Math.round(Math.round(meterBlockProcessTime.getOneMinuteRate() * 60));
        int gapMinuteCount = Math.round(Math.round(meterBlockTxCount.getOneMinuteRate() * 60));
        return gapMinuteTimeBlock / (double) gapMinuteCount;
      case 5:
        int gapFiveTimeBlock =
            Math.round(Math.round(meterBlockProcessTime.getFiveMinuteRate() * gap * 60));
        int gapFiveTimeCount =
            Math.round(Math.round(meterBlockTxCount.getFiveMinuteRate() * gap * 60));
        return gapFiveTimeBlock / (double) gapFiveTimeCount;
      case 15:
        int gapFifteenTimeBlock =
            Math.round(Math.round(meterBlockProcessTime.getFifteenMinuteRate() * gap * 60));
        int gapFifteenTimeCount =
            Math.round(Math.round(meterBlockTxCount.getFifteenMinuteRate() * gap * 60));
        return gapFifteenTimeBlock / (double) gapFifteenTimeCount;

      default:
        return -1;
    }
  }


}