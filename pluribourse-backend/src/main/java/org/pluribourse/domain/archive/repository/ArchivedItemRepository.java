package org.pluribourse.domain.archive.repository;

import org.pluribourse.domain.archive.entity.ArchivedItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArchivedItemRepository extends JpaRepository<ArchivedItem, Long> {

    List<ArchivedItem> findAllByEditionId(Long editionId);
}
