package model;

public enum ConnectorType {
    TYPE2("Type 2 (AC)",        new int[]{25, 1036}),
    CCS("CCS Combo 2 (DC)",     new int[]{33}),
    CHADEMO("CHAdeMO (DC)",     new int[]{2});

    public final String displayName;
    public final int[]  ocmIds;

    ConnectorType(String displayName, int[] ocmIds) {
        this.displayName = displayName;
        this.ocmIds      = ocmIds;
    }

    public static ConnectorType fromOcmId(int id) {
        for (ConnectorType ct : values())
            for (int ocmId : ct.ocmIds)
                if (ocmId == id) return ct;
        return null;
    }
}
