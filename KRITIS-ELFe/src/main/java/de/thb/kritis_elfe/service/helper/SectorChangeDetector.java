package de.thb.kritis_elfe.service.helper;

import de.thb.kritis_elfe.entity.Sector;

public class SectorChangeDetector {
    private Sector sector;

    public boolean isSectorChanged(Sector sector){
        if(this.sector == null){
            this.sector = sector;
            return true;
        }
        boolean result = this.sector != sector;

        if(result) {
            this.sector = sector;
        }

        return result;
    }

    public boolean isNotFirst(){
        return this.sector != null;
    }
}
