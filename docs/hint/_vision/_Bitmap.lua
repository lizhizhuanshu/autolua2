
---位图类，一切图像操作都是基于位图的
---@class Bitmap
local M = {}

---@alias ColorInt integer
---@alias ColorDescribe string|integer
---@alias ImageDescribe string

---特征描述格式为一个字符串"x|y|color"，其中x和y为坐标，color为颜色，例如"1|2|ffffff"
---@alias Feature string 

---获取指定坐标的颜色
---@param x integer 横轴坐标
---@param y integer 纵轴坐标
---@return ColorInt 坐标颜色，格式RGB
function M:getColor(x,y)
  error("not implemented")
end

---获取指定区域的符合颜色描述的坐标点数量
---@param x1 integer 范围的横轴起始点坐标
---@param y1 integer 范围的纵轴起始点坐标
---@param x2 integer 范围的横轴结束点坐标
---@param y2 integer 范围的纵轴结束点坐标
---@param color ColorDescribe 颜色描述
---@return integer 颜色计数
function M:getColorCount(x1,y1,x2,y2,color)
  error("not implemented")
end

---指定坐标是否符合颜色描述
---@param x integer 横轴坐标
---@param y integer 纵轴坐标
---@param color ColorDescribe 颜色描述
---@param sim number 相似度，取值范围 0-1
---@return boolean 是否符合颜色描述
function M:isColor(x,y,color,sim)
  error("not implemented")
end

---指定坐标是否符合颜色描述，返回符合描述的颜色的索引，没有符合则返回 0
---@param x integer 横轴坐标
---@param y integer 纵轴坐标
---@param color ColorDescribe 颜色描述
---@param sim number 相似度，取值范围 0-1
---@return integer 颜色索引，没有符合则返回 0
function M:whichColor(x,y,color,sim)
  error("not implemented")
end

---查找指定区域内符合颜色描述的坐标点,返回第一个符合的坐标
---@param x1 integer 要查找的范围的横轴起始点坐标
---@param y1 integer 要查找的范围的纵轴起始点坐标
---@param x2 integer 要查找的范围的横轴结束点坐标
---@param y2 integer 要查找的范围的纵轴结束点坐标
---@param color ColorDescribe 颜色描述
---@param order FindDirection 查找方向
---@param sim number 相似度，取值范围 0-1
---@return integer 找到的横轴坐标，没有找到则返回 -1
---@return integer 找到的纵轴坐标，没有找到则返回 -1
function M:findColor(x1,y1,x2,y2,color,order,sim)
  error("not implemented")
end

---是否符合特征描述
---@param feature Feature 特征描述
---@param sim integer 相似度，取值范围 0-100
---@return boolean 是否符合特征描述
function M:isFeature(feature,sim)
  error("not implemented")
end

---查找指定区域内符合特征描述的坐标点,返回第一个符合的坐标
---@param x1 integer 要查找的范围的横轴起始点坐标
---@param y1 integer 要查找的范围的纵轴起始点坐标
---@param x2 integer 要查找的范围的横轴结束点坐标
---@param y2 integer 要查找的范围的纵轴结束点坐标
---@param feature Feature 特征描述
---@param order FindDirection 查找方向
---@param sim number 相似度，取值范围 0-1
---@return integer 找到的横轴坐标，没有找到则返回 -1
---@return integer 找到的纵轴坐标，没有找到则返回 -1
function M:findFeature(x1,y1,x2,y2,feature,order,sim)
  error("not implemented")
end

---是否符合图像描述
---@param x integer 要对比图像的起点横轴坐标
---@param y integer 要对比图像的起点纵轴坐标
---@param image ImageDescribe 图像描述
---@param sim number 相似度，取值范围 0-1
---@return boolean 是否符合图像描述
function M:isImage(x,y,image,sim)
  error("not implemented")
end

---指定坐标是否符合图像描述，返回符合描述的图像的索引，没有符合则返回 0
---@param x integer 要对比图像的起点横轴坐标 
---@param y integer 要对比图像的起点纵轴坐标
---@param image ImageDescribe 图像描述
---@param sim number 相似度，取值范围 0-1
---@return integer 图像索引，没有符合则返回 0
function M:whichImage(x,y,image,sim)
  error("not implemented")
end

---查找指定区域内符合图像描述的坐标点,返回第一个符合的坐标
---@param x1 integer 要查找的范围的横轴起始点坐标
---@param y1 integer 要查找的范围的纵轴起始点坐标
---@param x2 integer 要查找的范围的横轴结束点坐标
---@param y2 integer 要查找的范围的纵轴结束点坐标
---@param image ImageDescribe 图像描述
---@param order FindDirection 查找方向
---@param sim number 相似度，取值范围 0-1
---@return integer 找到的横轴坐标，没有找到则返回 -1
---@return integer 找到的纵轴坐标，没有找到则返回 -1
function M:findImage(x1,y1,x2,y2,image,order,sim)
  error("not implemented")
end

---将指定区域的图像保存到指定路径
---@param path string 保存路径
---@param x1? integer 区域的横轴起始点坐标，不填则默认为 0
---@param y1? integer  区域的纵轴起始点坐标，不填则默认为 0
---@param x2? integer  区域的横轴结束点坐标，不填则默认为 位图宽度-1
---@param y2? integer  区域的纵轴结束点坐标，不填则默认为 位图高度-1
function M:save(path,x1,y1,x2,y2)
  error("not implemented")
end

---克隆指定区域的图像
---@param x1? integer 区域的横轴起始点坐标，不填则默认为 0
---@param y1? integer 区域的纵轴起始点坐标，不填则默认为 0
---@param x2? integer 区域的横轴结束点坐标，不填则默认为 位图宽度-1
---@param y2? integer 区域的纵轴结束点坐标，不填则默认为 位图高度-1
---@return Bitmap 克隆的位图
function M:clone(x1,y1,x2,y2)
  error("not implemented")
end

---获取位图的宽度和高度
---@return integer 位图宽度
---@return integer 位图高度
function M:getSize()
  error("not implemented")
end


return M 


