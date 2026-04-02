package com.nokia.ciq.processor.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON model written to each CRGROUP folder during CRGROUP-mode CIQ processing.
 *
 * <p>CRGROUP mode is triggered when the CIQ INDEX sheet has
 * {@code GROUP | CRGROUP | NODE} columns.  One {@code CRGroupIndex} is written per
 * unique CRGROUP value (e.g. "CR-001") and records every GROUP that contributes
 * nodes to that CR, along with the NIAM sub-mapping for each node.
 *
 * <p>A single CRGROUP may span multiple GROUPs:
 * <pre>
 *   GROUP | CRGROUP | NODE
 *   A     | CR-001  | MRF1
 *   A     | CR-001  | MRF2
 *   B     | CR-001  | MRF3   &lt;-- GROUP B node in the same CR
 *   B     | CR-002  | MRF4
 * </pre>
 * Results in {@code CR-001/} folder with entries for GROUP A (MRF1, MRF2) and
 * GROUP B (MRF3), plus separate {@code CR-002/} folder for GROUP B (MRF4).
 */
public class CRGroupIndex {

    private String nodeType;
    private String activity;
    private String crGroup;
    private List<GroupEntry> groups = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Nested: one entry per GROUP that contributes nodes to this CRGROUP
    // -------------------------------------------------------------------------

    public static class GroupEntry {

        private String group;
        private List<String> nodes       = new ArrayList<>();
        private Map<String, String> niamMapping = new LinkedHashMap<>();

        public String getGroup()                          { return group; }
        public void   setGroup(String group)              { this.group = group; }

        public List<String> getNodes()                    { return nodes; }
        public void         setNodes(List<String> nodes)  { this.nodes = nodes; }

        public Map<String, String> getNiamMapping()                      { return niamMapping; }
        public void                setNiamMapping(Map<String, String> m) { this.niamMapping = m; }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getNodeType()                       { return nodeType; }
    public void   setNodeType(String nodeType)        { this.nodeType = nodeType; }

    public String getActivity()                       { return activity; }
    public void   setActivity(String activity)        { this.activity = activity; }

    public String getCrGroup()                        { return crGroup; }
    public void   setCrGroup(String crGroup)          { this.crGroup = crGroup; }

    public List<GroupEntry> getGroups()               { return groups; }
    public void             setGroups(List<GroupEntry> groups) { this.groups = groups; }

    /** Returns all nodes across every GroupEntry in this CRGROUP. */
    public List<String> getAllNodes() {
        List<String> all = new ArrayList<>();
        for (GroupEntry ge : groups) all.addAll(ge.getNodes());
        return all;
    }
}
