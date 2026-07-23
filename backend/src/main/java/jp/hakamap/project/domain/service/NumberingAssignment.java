package jp.hakamap.project.domain.service;

import jp.hakamap.project.domain.value.GraveId;
import jp.hakamap.project.domain.value.ManagementNumber;

public record NumberingAssignment(GraveId graveId, ManagementNumber managementNumber) {}
