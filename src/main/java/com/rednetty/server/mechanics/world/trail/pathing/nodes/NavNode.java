package com.rednetty.server.mechanics.world.trail.pathing.nodes;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class NavNode implements Serializable {
    public int x, y, z;
    public double cost;
    public List<Neighbor> neighbors = new ArrayList<>();

    public NavNode(int x, int y, int z, double cost) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.cost = cost;
    }

    public static class Neighbor implements Serializable {
        public int index;
        public double transitionCost;

        public Neighbor(int index, double transitionCost) {
            this.index = index;
            this.transitionCost = transitionCost;
        }
    }
}
