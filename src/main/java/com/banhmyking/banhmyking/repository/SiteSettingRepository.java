package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.SiteSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteSettingRepository extends JpaRepository<SiteSetting, Long> {

    Optional<SiteSetting> findBySettingKey(String settingKey);
}
