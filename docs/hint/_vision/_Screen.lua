

---@class Screen:Bitmap
local M = {}

---获取初始的宽和高
---@return integer 宽度
---@return integer 高度
function M:getBaseSize()
  error("not implemented")
end

---获取初始的像素密度
---@return integer 像素密度 
function M:getBaseDensity()
  error("not implemented")
end

---获取旋转角度
---@return integer 旋转角度 0 是没有旋转，1 是逆时针旋转90度，2 是逆时针旋转180度，3 是逆时针旋转270度
function M:getRotation()
  error("not implemented")
end


---获取初始状态是横屏还是竖屏
---@return integer 1 竖屏，-1 横屏
function M:getBaseDirection()
  error("not implemented")
end


---当前方向是否改变
---@return boolean 手机当前方向与初始化的不同返回true，否则返回flase
function M:isChangeDirection()
  error("not implemented")
end

---是否保持位图数据不变
---@param is? boolean true保持不变,false不保持
function M:keepBitmap(is)
  error("not implemented")
end

---是否正在保持位图数据不变
---@return boolean
function M:isKeepingBitmap()
  error("not implemented")
end


---先刷新一下位图数据，然后保持不变
function M:updateAndKeepBitmap()
  error("not implemented")
end

---重置宽和高
---@param width integer 窗口的宽
---@param height integer 窗口的高
function M:resetSize(width,height)
  error("not implemented")
end



return M