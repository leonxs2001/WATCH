package de.thb.kritis_elfe.repository;

import de.thb.kritis_elfe.entity.Sector;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.RepositoryDefinition;

import java.util.List;
import java.util.Optional;

@RepositoryDefinition(domainClass = Sector.class, idClass = Long.class)
public interface SectorRepository extends CrudRepository<Sector, Long> {
    Sector findByName(String name);
    Optional<Sector> findById(Long id);
    @Override
    List<Sector> findAll();
}
