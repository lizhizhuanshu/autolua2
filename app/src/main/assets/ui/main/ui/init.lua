
local extra = window:getExtra()
local projectRootDir = extra:get("ROOT_DIR")
local projectResourceDir = extra:get("RESOURCE_DIR")
print(projectRootDir,projectResourceDir)
local function addSep(path)
  if path:sub(-1) ~= "/" then
    path = path .. "/"
  end
  return path
end


---@type string
---@diagnostic disable-next-line: assign-type-mismatch
local root = (projectResourceDir or ".")
root = addSep(root)


---返回当前项目资源的绝对路径
---@param path string 项目相对路径
---@return string
function rsrc(path)
  return root  .. path
end