package com.banhmyking.banhmyking.realtime;

import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;

/** Danh tính của một kết nối realtime — chụp lúc kết nối, đổi cơ sở có hiệu lực ở lần nối lại. */
public record RealtimeUser(Long userId, RoleName role, Long storeId) {

    public static RealtimeUser from(User user) {
        return new RealtimeUser(user.getId(), user.getRole(),
                user.getStore() == null ? null : user.getStore().getId());
    }
}
