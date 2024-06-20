
---模拟输入类
---@class Input
local M = {}




---模拟按下指定坐标，返回触摸id
---@param x integer 横轴坐标
---@param y integer 纵轴坐标
---@return integer 触摸id
function M:touchDown(x,y)
  error("not implemented")
end

---模拟移动指定触摸id到指定坐标
---@param id integer 触摸id
---@param x integer 横轴坐标
---@param y integer 纵轴坐标
function M:touchMove(id,x,y)
  error("not implemented")
end

---模拟抬起指定触摸id
---@param id integer 触摸id
function M:touchUp(id)
  error("not implemented")
end

---模拟点击指定坐标
---@param x integer 横轴坐标
---@param y integer 纵轴坐标
---@param time? integer 长按时间，单位毫秒
function M:tap(x,y,time)
  error("not implemented")
end

---模拟滑动
---@param x1 integer 滑动的起始横轴坐标
---@param y1 integer 滑动的起始纵轴坐标
---@param x2 integer 滑动的终点横轴坐标
---@param y2 integer 滑动的终点纵轴坐标
---@param time integer|nil 滑动的时间，单位毫秒
function M:swipe(x1,y1,x2,y2,time)
  error("not implemented")
end


---模拟点击指定按键
---@param key KeyCode 按键码
function M:keyPress(key)
  error("not implemented")
end

---模拟按下指定按键
---@param key KeyCode 按键码
function M:keyDown(key)
  error("not implemented")
end

---模拟抬起指定按键
---@param key KeyCode 按键码
function M:keyUp(key)
  error("not implemented")
end

---模拟输入文本
---@param text string 输入的文本
function M:text(text)
  error("not implemented")
end

---模拟输入文本使用输入法
---@param text string 输入的文本
function M:textByInputMethod(text)
  error("not implemented")
end



return M
