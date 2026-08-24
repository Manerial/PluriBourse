package org.pluribourse.domain.instanceconfig.service;

import lombok.*;
import org.pluribourse.domain.instanceconfig.dto.*;
import org.pluribourse.domain.instanceconfig.entity.*;
import org.pluribourse.domain.instanceconfig.mapper.*;
import org.pluribourse.domain.instanceconfig.repository.*;
import org.pluribourse.domain.user.enums.*;
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
        mapper.updateConfigFromDto(dto, config);
        return mapper.toDto(repository.save(config));
    }

    @Transactional(readOnly = true)
    public Language getDefaultDocumentLanguage() {
        return findConfig().getDefaultDocumentLanguage();
    }

    @Transactional(readOnly = true)
    public BigDecimal getDefaultCommissionRate() {
        return findConfig().getDefaultCommissionRate();
    }

    @Transactional(readOnly = true)
    public String getAssociationName() {
        return findConfig().getAssociationName();
    }

    private GlobalInstanceConfig findConfig() {
        return repository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("global_instance_config row missing — ensure migration 004 ran"));
    }
}
