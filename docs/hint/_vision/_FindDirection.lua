---图色函数的查找方向
---@enum FindDirection
local M = {
  ---从上到下，从左到右
  UP_DOWN_LEFT_RIGHT=0,
  ---从上到下，从右到左
  UP_DOWN_RIGHT_LEFT=1,
  ---从下到上，从左到右
  DOWN_UP_LEFT_RIGHT=2,
  ---从下到上，从右到左
  DOWN_UP_RIGHT_LEFT=3,
  ---从左到右，从上到下
  LEFT_RIGHT_UP_DOWN=4,
  ---从左到右，从下到上
  LEFT_RIGHT_DOWN_UP=5,
  ---从右到左，从上到下
  RIGHT_LEFT_UP_DOWN=6,
  ---从右到左，从下到上
  RIGHT_LEFT_DOWN_UP=7,
}



return M