package com.leyou.item.pojo;

import lombok.Data;

import javax.persistence.Transient;
import java.util.List;

@Data
public class ChildCates extends Category{
    @Transient
    private List<Category> childCates;
}