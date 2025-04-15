package com.rednetty.server.mechanics.world.trail.pathing.nodes;

import java.io.*;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class OptimizedNodeMapStorage {

    /**
     * Saves the given list of nodes to a file in a compact binary format.
     * The file is compressed with GZIP.
     * <p>
     * Format:
     * - int: nodeCount
     * - For each node:
     * int x, int y, int z, double cost
     * - For each node:
     * int neighborCount
     * For each neighbor:
     * int neighborIndex, double transitionCost
     *
     * @param nodes the list of NavNode to save
     * @param file  the destination file
     * @throws IOException if an I/O error occurs
     */
    public static void saveOptimizedNodeMap(List<NavNode> nodes, File file) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))))) {
            // Write the number of nodes
            dos.writeInt(nodes.size());
            // Write node data
            for (NavNode node : nodes) {
                dos.writeInt(node.x);
                dos.writeInt(node.y);
                dos.writeInt(node.z);
                dos.writeDouble(node.cost);
            }
            // Write neighbor data for each node
            for (NavNode node : nodes) {
                dos.writeInt(node.neighbors.size());
                for (NavNode.Neighbor neighbor : node.neighbors) {
                    dos.writeInt(neighbor.index);
                    dos.writeDouble(neighbor.transitionCost);
                }
            }
        }
    }

    /**
     * Loads the node map from a file that was written using the saveOptimizedNodeMap method.
     *
     * @param file the file to load from
     * @return the list of NavNode
     * @throws IOException if an I/O error occurs
     */
    public static List<NavNode> loadOptimizedNodeMap(File file) throws IOException {
        List<NavNode> nodes = new java.util.ArrayList<>();
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {
            int nodeCount = dis.readInt();
            // Read node data first
            for (int i = 0; i < nodeCount; i++) {
                int x = dis.readInt();
                int y = dis.readInt();
                int z = dis.readInt();
                double cost = dis.readDouble();
                nodes.add(new NavNode(x, y, z, cost));
            }
            // Now read neighbor data for each node
            for (int i = 0; i < nodeCount; i++) {
                int neighborCount = dis.readInt();
                NavNode node = nodes.get(i);
                for (int j = 0; j < neighborCount; j++) {
                    int neighborIndex = dis.readInt();
                    double transitionCost = dis.readDouble();
                    node.neighbors.add(new NavNode.Neighbor(neighborIndex, transitionCost));
                }
            }
        }
        return nodes;
    }
}
