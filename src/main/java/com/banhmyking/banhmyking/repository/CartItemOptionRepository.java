package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.CartItemOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface CartItemOptionRepository extends JpaRepository<CartItemOption, Long> {

    /** Đếm dòng giỏ hàng đang tham chiếu các option này — chặn xóa option còn FK sống. */
    long countByProductOption_IdIn(Collection<Long> optionIds);
}
