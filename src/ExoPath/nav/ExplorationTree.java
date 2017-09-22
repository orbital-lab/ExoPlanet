/* . . . . . . . . . . . . . . . . . . . . . . . . . . . .
 * (c) Stefan Kral 2011 (http://www.redfibre.net/orbital)
 *                     _   _ _       _
 *             ___ ___| |_|_| |_ ___| |
 * _______    | . |  _| . | |  _| .'| |     _____________
 *       /____|___|_| |___|_|_| |__,|_|____/
 *
 * This program is free software and you are welcome to
 * modify and/or redistribute it under the terms of the
 * GNU General Public License http://www.gnu.org/licenses.
 * . . . . . . . . . . . . . . . . . . . . . . . . . . . . */

package exopath.nav;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import exopath.nav.NavigationTask.TreeNode;

/**
 * The ExplorationTree data type. This is a recursive structure of subtrees.
 */
public class ExplorationTree {

  /** The subtree id. Each subtree has a unique id to have a indicator for the actual position in the tree.
   *  The id is just a counter based on the id value of the root node (so root has the highest id). */
  private int id = 0;

  /** The associated node entry. */
  private TreeNode node;

  /** The leaf subtrees. */
  private final ArrayList<ExplorationTree> leafs = new ArrayList<ExplorationTree>();

  /** The parent tree reference. */
  private transient ExplorationTree parent = null;

  /** The map of all node entries to its corresponding subtree. */
  private transient HashMap<TreeNode, ExplorationTree> treeMap = new HashMap<TreeNode, ExplorationTree>();

  /**
   * Instantiates a new empty exploration tree.
   */
  public ExplorationTree() {
  }

  /**
   * Instantiates a new exploration tree and map the given node to it.
   *
   * @param node the node associated with this (sub)tree
   */
  public ExplorationTree(TreeNode node) {
    this.node = node;
    treeMap.put(node, this);
  }

  /**
   * A helper method to rebuild the exploration tree from saved representation.
   *
   * @param id the id of the subtree to which the exploration tree reference should point to
   *           (the actual position in the tree)
   * @return the rebuild exploration tree
   */
  public ExplorationTree rebuildTree(int id) {

      ExplorationTree currentTree = null;     // to find the subtree with the given id
      treeMap.put(node, this);                // put the actual node and tree to the tree map
      for (ExplorationTree subtree : leafs) { // iterate over all subtrees
          subtree.parent = this;              // restore parent relation
          subtree.treeMap = this.treeMap;     // give a reference to the treemap
          ExplorationTree t = subtree.rebuildTree(id); // rebuild subtrees recursively
          if (t != null)                      // check if a subtree with the given id was found
              currentTree = t;
      }
      return this.id == id ? this : currentTree; // return the actual subtree
  }

  /**
   * Gets the current (sub)tree id.
   *
   * @return the tree id
   */
  public int getID() {
      return id;
  }

  /**
   * Adds a subtree to the exploration tree as leaf.
   *
   * @param leaf the leaf subtree
   * @return the reference to the new subtree
   */
  public ExplorationTree addLeaf(TreeNode leaf) {
    if (isEmpty()) { // when tree is empty, use root (this)
      this.node = leaf;
      treeMap.put(leaf, this);
      return this;
    }
    else {
      ExplorationTree t = new ExplorationTree(leaf); // create new subtree
      leafs.add(t);             // add the new subtree to the list of leafs
      t.parent = this;          // set parent relation
      t.treeMap = this.treeMap; // save treeMap reference
      treeMap.put(leaf, t);     // add the subtree to the treeMap

      t.id = getRoot().id;      // set the id as the highest by copy root id
      getRoot().id++;           // decrease the root id value

      return t;                 // return a reference to the new subtree
    }
  }

  /**
   * Gets the node entry of this (sub)tree.
   *
   * @return the node entry
   */
  public TreeNode getNode() {
    return node;
  }

  /**
   * Gets the root tree.
   *
   * @return the root tree
   */
  public ExplorationTree getRoot() {
      ExplorationTree t = treeMap.get(node); // get node entry for parent relation
      while (t.getParent() != null)          // and follow parent references up to the root
          t = t.getParent();

      return t;
  }

  /**
   * Gets the parent tree.
   *
   * @return the parent tree
   */
  public ExplorationTree getParent() {
    return parent;
  }

  /**
   * Gets the leaf sub trees.
   *
   * @return the sub trees
   */
  public Collection<ExplorationTree> getSubTrees() {
    return leafs;
  }

  /**
   * Checks if the exploration tree is empty (no node entries are available).
   *
   * @return true, if tree is empty
   */
  public boolean isEmpty() {
      return treeMap.size() == 0;
  }

  @Override
  public String toString() {
    return printTree(0);
  }

  /**
   * Prints the tree formatted to a String.
   *
   * @param deep the actual tree deepness for indentation
   * @return the formatted string representation of the exploration tree
   */
  private String printTree(int deep) {
    String s = "";
    String indent = "";
    for (int i = 0; i < deep; ++i) {
      indent = indent + " ";
    }
    s = indent + node;
    for (ExplorationTree child : leafs) {
      s += "\n" + child.printTree(deep + 1);
    }
    return s;
  }
}
