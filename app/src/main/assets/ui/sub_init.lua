

local extra = window:getExtra()
local projectName = extra:get("projectName")
local projectRootPath = extra:get("projectRootPath")
local projectResourceDir = extra:get("projectResourceDir")

do
  local oldPreferenceUtils = PreferenceUtils
  local _name = projectName .. "."
  local M = {}
  function M:get(key,default)
    return oldPreferenceUtils:get(_name .. key,default)
  end

  function M:save(key,value)
    return oldPreferenceUtils:save(_name .. key,value)
  end
  PreferenceUtils = M
end


do
  ---@type string
---@diagnostic disable-next-line: assign-type-mismatch
  local root = projectRootPath .. (projectResourceDir or "")
  if root:sub(-1) ~= "/" then
    root = root .. "/"
  end
  
  ---返回当前项目资源的绝对路径
  ---@param path string 项目相对路径
  ---@return string
  function rsrc(path)
    return projectRootPath  .. path
  end
end



