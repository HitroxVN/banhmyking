package com.banhmyking.banhmyking.enums;

/** 1 user · 1 role — ADMIN thừa kế quyền STAFF. */
public enum RoleName {
    CUSTOMER,
    STAFF,
    SHIPPER,
    /** Quản lý một cơ sở: vận hành + báo cáo + xem nhân viên của cơ sở mình. */
    MANAGER,
    ADMIN
}
