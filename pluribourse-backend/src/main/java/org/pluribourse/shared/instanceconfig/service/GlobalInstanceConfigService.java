package org.pluribourse.shared.instanceconfig.service;

import lombok.*;
import org.pluribourse.shared.instanceconfig.dto.*;
import org.pluribourse.shared.instanceconfig.entity.*;
import org.pluribourse.shared.instanceconfig.mapper.*;
import org.pluribourse.shared.instanceconfig.repository.*;
import org.pluribourse.user.enums.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import java.math.*;

@Service
@RequiredArgsConstructor
public class GlobalInstanceConfigService {

    private final GlobalInstanceConfigRepository repository;
    private final GlobalInstanceConfigMapper mapper;

    @Transactional(readOnly = true)
    public GlobalInstanceConfigDto getConfig() {
        return mapper.toDto(findConfig());
    }

    @Transactional
    public GlobalInstanceConfigDto updateConfig(GlobalInstanceConfigDto dto) {
        GlobalInstanceConfig config = findConfig();
        config.setAssociationName(dto.associationName());
        config.setDefaultCommissionRate(dto.defaultCommissionRate());
        config.setDefaultDocumentLanguage(dto.defaultDocumentLanguage());
        return mapper.toDto(repository.save(config));
    }

    // Used by Story 2.1 (EditionService) to initialize new editions.
    @Transactional(readOnly = true)
    public Language getDefaultDocumentLanguage() {
        return findConfig().getDefaultDocumentLanguage();
    }

    // Used by Story 2.1 (EditionService) to initialize new editions.
    @Transactional(readOnly = true)
    public BigDecimal getDefaultCommissionRate() {
        return findConfig().getDefaultCommissionRate();
    }

    private GlobalInstanceConfig findConfig() {
        return repository.findById(1L)
                .orElseThrow(() -> new IllegalStateException(
                        "global_instance_config row missing — ensure migration 004 ran"));
    }
}
