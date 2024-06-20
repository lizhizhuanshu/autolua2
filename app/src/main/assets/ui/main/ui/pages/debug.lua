local M = {}
---@param content View
function M.init(content)
  local labat = Label()
  labat:text("lizhzhuanshu")
  content:addView(labat)
end


return M 