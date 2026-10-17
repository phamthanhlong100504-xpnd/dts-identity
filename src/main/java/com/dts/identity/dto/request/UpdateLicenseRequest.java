package com.dts.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateLicenseRequest(@NotBlank String licenseClass) {}