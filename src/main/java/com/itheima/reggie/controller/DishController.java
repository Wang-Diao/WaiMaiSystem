package com.itheima.reggie.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.reggie.common.CustomException;
import com.itheima.reggie.common.R;
import com.itheima.reggie.dto.DishDto;
import com.itheima.reggie.entity.Category;
import com.itheima.reggie.entity.Dish;
import com.itheima.reggie.entity.DishFlavor;
import com.itheima.reggie.entity.Employee;
import com.itheima.reggie.service.CategoryService;
import com.itheima.reggie.service.DishFlavorService;
import com.itheima.reggie.service.DishService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/dish")
@Slf4j
public class DishController {
    @Autowired
    private DishService dishService;

    @Autowired
    private DishFlavorService dishFlavorService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private RedisTemplate redisTemplate;

    @PostMapping
    public R<String> save(@RequestBody DishDto dishDto){
        log.info(dishDto.toString());
        dishService.saveWithFlavor(dishDto);

        //清理所有菜品缓存
        Set keys = redisTemplate.keys("dish_*");
        redisTemplate.delete(keys);

        //清理某个分类下面的菜品缓存数据
        //String key = "dish_" + dishDto.getCategoryId() + "_1";
        //redisTemplate.delete(key);

        return R.success("新增菜品成功");
    }

    @GetMapping("/page")
    public R<Page> page(int page,int pageSize,String name){

        //构造分页构造器
        Page<Dish> pageInfo = new Page<>(page,pageSize);
        Page<DishDto> dishDtoPage = new Page<>();
        //条件构造器
        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper();
        //添加过滤条件
        queryWrapper.like(name != null,Dish::getName,name);
        //添加排序条件
        queryWrapper.orderByDesc(Dish::getUpdateTime);
        //执行分页查询
        dishService.page(pageInfo,queryWrapper);
        //对象拷贝,将除了records外的其余属性都copy进去（分页的数据）
        BeanUtils.copyProperties(pageInfo,dishDtoPage,"records");

        List<Dish> records = pageInfo.getRecords();

        //list最终的数据是修改后的records
        List<DishDto> list = records.stream().map((item) -> {
            DishDto dishDto = new DishDto();
            //将Dish里的数据copy
            BeanUtils.copyProperties(item,dishDto);   //dishDto里面拿到了records，然后对records做了修改，再return
            Long categoryId = item.getCategoryId();
            //根据id查询分类对象
            Category category = categoryService.getById(categoryId);
            if(category !=null){
                String categoryName = category.getName();
                dishDto.setCategoryName(categoryName);
            }

            return dishDto;
        }).collect(Collectors.toList());

        dishDtoPage.setRecords(list);
        return R.success(dishDtoPage);
    }

    //根据id查询菜品信息和口味信息
    @GetMapping("/{id}")
    public R<DishDto> get(@PathVariable Long id){
        DishDto dishDto = dishService.getByIdWithFlavor(id);
        return R.success(dishDto);
    }

    //修改菜品
    @PutMapping
    public R<String> update(@RequestBody DishDto dishDto){
        //查找当前菜品对应的categoryId（前端给的是修改过的，要拿之前的categoryId），然后在缓存中删除，防止下面的bug发生
        Dish dish = dishService.getById(dishDto.getId());
        //查询出来的是数据库里的categoryId
        Long categoryId = dish.getCategoryId();
        log.info("修改前的categoryId为：" + categoryId);
        String key1 = "dish_" + categoryId + "_1";
        redisTemplate.delete(key1);

        log.info(dishDto.toString());
        dishService.updateWithFlavor(dishDto);



        //清理所有菜品缓存
        //Set keys = redisTemplate.keys("dish_*");
        //redisTemplate.delete(keys);

        //清理某个分类下面的菜品缓存数据
        //bug:将菜品的种类进行更改时，会导致这个菜品出现在两个种类中(前端拿到的dishDto.getCategoryId()已经是改过的)
        //上面已修改
        String key = "dish_" + dishDto.getCategoryId() + "_1";
        redisTemplate.delete(key);


        return R.success("修改菜品成功");
    }

    //根据id修改菜品状态(起售或停售)
    @PostMapping("/status/{status}")
    public R<String> updateById(@PathVariable Integer status,Long[] ids){
        for (Long id : ids) {
            Dish dish = dishService.getById(id);
            if(dish != null){
                dish.setStatus(status);
                dishService.updateById(dish);
            }
        }

        return R.success("菜品状态修改成功");
    }

    //@RequestBody传的参数主要是json字符串，@RequestParam传的参数可以是基本元素，数组，对象，集合等
    //批量删除
    @DeleteMapping
    public R<String> delete(@RequestParam List<Long> ids){   //或者Long[] ids 或者@RequestParam Long[] ids
        //删除dish_flavor里的数据
        LambdaQueryWrapper<DishFlavor> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(DishFlavor::getDishId,ids);
        dishFlavorService.remove(queryWrapper);

        //删除dish里的数据
        LambdaQueryWrapper<Dish> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.in(Dish::getId,ids);
        lambdaQueryWrapper.eq(Dish::getStatus,1);
        int count = dishService.count(lambdaQueryWrapper);
        if(count > 0){
            //如果不能删除，抛出一个业务逻辑
            throw new CustomException("菜品正在售卖中，不能删除");
        }
        for (Long id : ids) {
            dishService.removeById(id);
        }

        return R.success("删除成功");
    }

    /*//根据条件查询菜品数据
    @GetMapping("/list")
    public R<List<Dish>> list(Dish dish){
        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(dish.getCategoryId() != null,Dish::getCategoryId,dish.getCategoryId());
        //查询起售状态是1的菜品
        queryWrapper.eq(Dish::getStatus,1);
        List<Dish> list = dishService.list(queryWrapper);
        return R.success(list);
    }*/

    //根据条件查询菜品数据
    @GetMapping("/list")
    public R<List<DishDto>> list(Dish dish){
        List<DishDto> dishDtoList = null;
        String key = "dish_" + dish.getCategoryId() + "_" + dish.getStatus();
        //先从redis中获取缓存数据
        dishDtoList = (List<DishDto>) redisTemplate.opsForValue().get(key);
        if(dishDtoList != null){
            //如果存在，直接返回，无需查询数据库
            return R.success(dishDtoList);
        }

        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(dish.getCategoryId() != null,Dish::getCategoryId,dish.getCategoryId());
        //查询起售状态是1的菜品
        queryWrapper.eq(Dish::getStatus,1);
        List<Dish> list = dishService.list(queryWrapper);

        dishDtoList = list.stream().map((item) -> {
            DishDto dishDto = new DishDto();
            //将Dish里的数据copy
            BeanUtils.copyProperties(item,dishDto);   //dishDto里面拿到了records，然后对records做了修改，再return
            Long categoryId = item.getCategoryId();
            //根据id查询分类对象
            Category category = categoryService.getById(categoryId);
            if(category !=null){
                String categoryName = category.getName();
                dishDto.setCategoryName(categoryName);
            }

            //当前菜品id
            Long dishId = item.getId();
            LambdaQueryWrapper<DishFlavor> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(DishFlavor::getDishId,dishId);
            List<DishFlavor> dishFlavorList = dishFlavorService.list(lambdaQueryWrapper);

            dishDto.setFlavors(dishFlavorList);
            return dishDto;
        }).collect(Collectors.toList());

        //如果不存在，需要查询数据库，将查询到的菜品数据缓存到redis中
        redisTemplate.opsForValue().set(key,dishDtoList,60, TimeUnit.MINUTES);

        return R.success(dishDtoList);
    }


}
