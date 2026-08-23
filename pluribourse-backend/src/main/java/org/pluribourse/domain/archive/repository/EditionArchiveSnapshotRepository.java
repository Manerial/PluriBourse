package org.pluribourse.domain.archive.repository;

import org.pluribourse.domain.archive.entity.EditionArchiveSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EditionArchiveSnapshotRepository extends JpaRepository<EditionArchiveSnapshot, Long> {
}
