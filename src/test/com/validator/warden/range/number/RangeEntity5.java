package com.validator.warden.range.number;

import com.validator.warden.annotation.Matcher;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author DandyLuo
 */
@Data
@Accessors(chain = true)
public class RangeEntity5 {

    /**
     * 属性为大于等于100
     */
    @Matcher(range = "(100, )")
    private Integer num1;

    @Matcher(range = "[100,)")
    private Integer num2;

    @Matcher(range = "(, 50)")
    private Integer num3;

    @Matcher(range = "(,50]")
    private Integer num4;
}
