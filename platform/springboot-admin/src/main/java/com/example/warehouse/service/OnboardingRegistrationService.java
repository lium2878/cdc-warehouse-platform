package com.example.warehouse.service;

import com.example.warehouse.model.SparkTaskConfig;
import com.example.warehouse.model.TableMetadata;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingRegistrationService {
    private final MetadataService metadataService;
    private final TaskConfigService taskConfigService;

    public OnboardingRegistrationService(MetadataService metadataService,
                                         TaskConfigService taskConfigService) {
        this.metadataService = metadataService;
        this.taskConfigService = taskConfigService;
    }

    @Transactional
    public void register(TableMetadata table, SparkTaskConfig task) {
        metadataService.saveTable(table);
        taskConfigService.saveTask(task);
    }
}
