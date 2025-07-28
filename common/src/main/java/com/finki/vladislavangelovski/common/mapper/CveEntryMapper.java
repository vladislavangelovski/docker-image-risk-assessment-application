package com.finki.vladislavangelovski.common.mapper;

import com.finki.vladislavangelovski.common.model.CveEntry;
import com.finki.vladislavangelovski.common.dto.CveEntryDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CveEntryMapper {
    CveEntryDto toDto(CveEntry entry);
    CveEntry toModel(CveEntryDto dto);
}
