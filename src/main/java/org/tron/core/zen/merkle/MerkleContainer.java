package org.tron.core.zen.merkle;

import com.google.protobuf.ByteString;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.tron.core.capsule.IncrementalMerkleTreeCapsule;
import org.tron.core.capsule.IncrementalMerkleVoucherCapsule;
import org.tron.core.capsule.PedersenHashCapsule;
import org.tron.core.db.Manager;

@Slf4j
public class MerkleContainer {

  @Setter
  @Getter
  private Manager manager;

  public static MerkleContainer createInstance(Manager manager) {
    MerkleContainer instance = new MerkleContainer();
    instance.setManager(manager);
    return instance;
  }

  public static byte[] lastTreeKey = "LAST_TREE".getBytes();
  public static byte[] currentTreeKey = "CURRENT_TREE".getBytes();

  public IncrementalMerkleTreeContainer getCurrentMerkle() {
    IncrementalMerkleTreeCapsule capsule = manager.getMerkleTreeStore().get(currentTreeKey);
    if (capsule == null) {
      return getBestMerkle();
    }
    return capsule.toMerkleTreeContainer();
  }

  public IncrementalMerkleTreeContainer getBestMerkle() {
    IncrementalMerkleTreeCapsule capsule = manager.getMerkleTreeStore().get(lastTreeKey);
    if (capsule == null) {
      IncrementalMerkleTreeContainer container =
          (new IncrementalMerkleTreeCapsule()).toMerkleTreeContainer();
      return container;
    }
    return capsule.toMerkleTreeContainer();
  }

  public void saveCurrentMerkleTreeAsBestMerkleTree(long blockNum) {
    IncrementalMerkleTreeContainer treeContainer = getCurrentMerkle();
    setBestMerkle(blockNum, treeContainer);
    putMerkleTreeIntoStore(treeContainer.getMerkleTreeKey(), treeContainer.getTreeCapsule());
  }

  public void setBestMerkle(long blockNum, IncrementalMerkleTreeContainer treeContainer) {
    manager.getMerkleTreeStore().put(lastTreeKey, treeContainer.getTreeCapsule());
    manager.getMerkleTreeIndexStore().put(blockNum, treeContainer.getMerkleTreeKey());
  }

  public void setCurrentMerkle(IncrementalMerkleTreeContainer treeContainer) {
    manager.getMerkleTreeStore().put(currentTreeKey, treeContainer.getTreeCapsule());
  }

  public boolean merkleRootExist(byte[] rt) {
    return this.manager.getMerkleTreeStore().contain(rt);
  }

  public IncrementalMerkleTreeCapsule getMerkleTree(byte[] rt) {
    return this.manager.getMerkleTreeStore().get(rt);
  }

  public IncrementalMerkleTreeContainer saveCmIntoMerkleTree(
      IncrementalMerkleTreeContainer tree, byte[] cm) {

    PedersenHashCapsule sha256CompressCapsule1 = new PedersenHashCapsule();
    sha256CompressCapsule1.setContent(ByteString.copyFrom(cm));
    tree.append(sha256CompressCapsule1.getInstance());

    return tree;
  }

  public void putMerkleTreeIntoStore(byte[] key, IncrementalMerkleTreeCapsule capsule) {
    this.manager.getMerkleTreeStore().put(key, capsule);
  }

  public MerklePath merklePath(byte[] rt) {
    if(!merkleRootExist(rt)){
      return null;
    }

    IncrementalMerkleTreeContainer tree =
        this.manager.getMerkleTreeStore().get(rt).toMerkleTreeContainer();
    return tree.path();
  }

}