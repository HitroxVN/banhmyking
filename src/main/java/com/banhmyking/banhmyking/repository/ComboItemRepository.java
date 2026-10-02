package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.ComboItemId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ComboItemRepository extends JpaRepository<ComboItem, ComboItemId> {

    /** Tên các combo CHƯA xoá đang chứa món — dùng để chặn xoá món lẻ (spec §5). */
    @Query("SELECT DISTINCT ci.combo.name FROM ComboItem ci "
            + "WHERE ci.component.id = :componentId AND ci.combo.deleted = false "
            + "ORDER BY ci.combo.name")
    List<String> findActiveComboNamesContaining(@Param("componentId") Long componentId);
}
