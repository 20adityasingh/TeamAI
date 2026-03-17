package com.distributed.teamai.account_service.service;

import com.distributed.teamai.common_lib.dto.PlanDto;
import java.util.List;

public interface PlanService {
    List<PlanDto> getAllActivePlans();
}
