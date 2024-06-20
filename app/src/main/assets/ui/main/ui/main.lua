
require "init"
local layout = LinearLayout(LinearType.VERTICAL)
layout:width(MeasurementType.MATCH_PARENT)
layout:height(MeasurementType.MATCH_PARENT)

local content = View()
content:width(MeasurementType.MATCH_PARENT)
content:bgColor(Color(255,0,0,0.5))
content:weight(14)

local function switchTo(name)
  content:removeAllSubviews()
  local page = require("pages."..name)
  if type(page) == "table" then
    page.init(content)
  end
end


local function makeNaviagtion(name)
  local url = name .. ".svg"
  local r= ImageButton()
  print(rsrc(url))
  r:setImage(rsrc(url))
  r:height(MeasurementType.MATCH_PARENT)
  r:weight(1)
  r:onClick(function ()
    switchTo(name)
  end)
  return r
end

local naviagtionView = LinearLayout(LinearType.HORIZONTAL)
naviagtionView:width(MeasurementType.MATCH_PARENT)
naviagtionView:bgColor(Color(0,0,0,0.5))
naviagtionView:addView(makeNaviagtion("debug"))
naviagtionView:addView(makeNaviagtion("main"))
naviagtionView:addView(makeNaviagtion("account"))
naviagtionView:weight(1)
switchTo("debug")

layout:addView(content)
layout:addView(naviagtionView)
window:addView(layout)


