local config = { monitor = nil, scale = 1, refresh = 1, page = 6, feed = 6 }

local USAGE = "commandroom [monitor=<side or name>] [scale=1] [refresh=1] [page=6] [feed=6]"
local NUMBERS = { scale = true, refresh = true, page = true, feed = true }

for _, arg in ipairs({ ... }) do
    local key, value = arg:match("^(%w+)=(.+)$")
    if not key or config[key] == nil and key ~= "monitor" then
        error("unknown option '" .. arg .. "', use: " .. USAGE, 0)
    end
    if NUMBERS[key] then
        config[key] = tonumber(value) or error(key .. " must be a number", 0)
    else
        config[key] = value
    end
end

local out = term.current()
if config.monitor then
    out = peripheral.wrap(config.monitor) or error("no monitor named " .. config.monitor, 0)
    out.setTextScale(config.scale)
end

local trains, byId = {}, {}
local seen, speedAvg, danger = {}, {}, {}
local feed = {}
local page, lastFlip = 0, os.clock()
local primed = false

local function nameOf(t)
    return t.name ~= "" and t.name or ("Train " .. t.id:sub(1, 8))
end

local function nameFor(id)
    return byId[id] and nameOf(byId[id]) or id:sub(1, 8)
end

local function log(text, color)
    feed[#feed + 1] = { time = textutils.formatTime(os.time(), true), text = text, color = color }
    if #feed > 50 then table.remove(feed, 1) end
end

local function duration(seconds)
    if seconds < 60 then return string.format("%ds", seconds) end
    if seconds < 3600 then return string.format("%dm%02d", seconds / 60, seconds % 60) end
    return string.format("%dh%02d", seconds / 3600, seconds % 3600 / 60)
end

local function etaOf(t)
    local avg = speedAvg[t.id] or 0
    if not t.remaining or avg < 0.01 then return nil end
    return t.remaining / avg / 20
end

local function classify(t)
    if t.derailed then return "DERAILED", colors.red, 0 end
    if danger[t.id] and danger[t.id] > os.clock() then return "RAN RED", colors.red, 0 end
    if t.paused then return "paused", colors.yellow, 2 end
    if t.heldAtRed then return "held red", colors.orange, 1 end
    if t.stationState == "stopped" then return "at stop", colors.white, 4 end
    if t.stationState == "stopping" then return "arriving", colors.lime, 3 end
    if t.stationState == "departing" then return "leaving", colors.lime, 3 end
    if math.abs(t.speed) > 0.01 then return "running", colors.lime, 3 end
    return "standing", colors.lightGray, 4
end

local function whereOf(t)
    if t.stationState == "stopped" and t.station then return "at " .. t.station end
    if t.stationState == "stopping" and (t.station or t.target) then return "arr " .. (t.station or t.target) end
    if t.target then return "to " .. t.target end
    return t.station and ("near " .. t.station) or "no destination"
end

local function notesOf(t)
    if t.heldReason then return t.heldReason end
    if t.stationState == "stopped" then return "dwell " .. duration(t.dwellTicks / 20) end
    if t.scheduleSize and t.scheduleSize > 0 then
        return string.format("stop %d of %d", t.scheduleEntry, t.scheduleSize)
    end
    return ""
end

local function compare(a, b)
    local _, _, ra = classify(a)
    local _, _, rb = classify(b)
    if ra ~= rb then return ra < rb end
    local ea, eb = etaOf(a), etaOf(b)
    if ea and eb and ea ~= eb then return ea < eb end
    if (ea ~= nil) ~= (eb ~= nil) then return ea ~= nil end
    return nameOf(a) < nameOf(b)
end

local function compareStates(t)
    local before = seen[t.id]
    local name = nameOf(t)
    if before and primed then
        if t.derailed and not before.derailed then log(name .. " DERAILED", colors.red) end
        if before.derailed and not t.derailed then log(name .. " back on the rails", colors.lime) end
        if t.stationState == "stopped" and before.stationState ~= "stopped" then
            log(name .. " arrived at " .. (t.station or "a stop"), colors.white)
        end
        if before.stationState == "stopped" and t.stationState ~= "stopped" then
            log(name .. " left " .. (before.station or "a stop"), colors.lightGray)
        end
        if t.heldAtRed and not before.heldAtRed then log(name .. " held at a red", colors.orange) end
        if before.heldAtRed and not t.heldAtRed then log(name .. " signal cleared", colors.lightGray) end
    end
    seen[t.id] = { derailed = t.derailed, stationState = t.stationState, station = t.station, heldAtRed = t.heldAtRed }
end

local function update()
    trains = loconautics.getTrains()
    byId = {}
    for _, t in ipairs(trains) do
        byId[t.id] = t
        local speed = math.abs(t.speed)
        speedAvg[t.id] = speedAvg[t.id] and (speedAvg[t.id] * 0.7 + speed * 0.3) or speed
        compareStates(t)
    end
    for id in pairs(seen) do
        if not byId[id] then seen[id], speedAvg[id] = nil, nil end
    end
    table.sort(trains, compare)
    primed = true
end

local function put(y, text, color, background)
    local w = out.getSize()
    out.setCursorPos(1, y)
    out.setBackgroundColor(background or colors.black)
    out.setTextColor(color or colors.white)
    out.write((text .. string.rep(" ", w)):sub(1, w))
end

local function draw()
    local W, H = out.getSize()
    out.setBackgroundColor(colors.black)
    out.clear()

    local counts = { moving = 0, stopped = 0, held = 0, alert = 0 }
    for _, t in ipairs(trains) do
        local _, _, rank = classify(t)
        if rank == 0 then counts.alert = counts.alert + 1
        elseif rank <= 2 then counts.held = counts.held + 1
        elseif t.stationState == "stopped" or math.abs(t.speed) <= 0.01 then counts.stopped = counts.stopped + 1
        else counts.moving = counts.moving + 1 end
    end
    put(1, string.format(" COMMAND ROOM  %s   %d trains  %d moving  %d stopped  %d held  %d alerts",
        textutils.formatTime(os.time(), true), #trains, counts.moving, counts.stopped, counts.held, counts.alert),
        colors.white, counts.alert > 0 and colors.red or colors.blue)

    local notes = W >= 72 and 16 or 0
    local nameW, stateW, etaW, spdW = 12, 9, 7, 7
    local whereW = math.max(10, W - nameW - stateW - etaW - spdW - notes - 5)
    local function row(y, name, state, where, eta, spd, note, color, background)
        local line = string.format("%-" .. nameW .. "." .. nameW .. "s %-" .. stateW .. "." .. stateW .. "s %-"
            .. whereW .. "." .. whereW .. "s %" .. etaW .. "s %" .. spdW .. "s", name, state, where, eta, spd)
        if notes > 0 then line = line .. " " .. string.format("%-" .. notes .. "." .. notes .. "s", note) end
        put(y, " " .. line, color, background)
    end
    row(2, "TRAIN", "STATE", "WHERE", "ETA", "B/S", "NOTES", colors.black, colors.lightGray)

    local feedRows = math.min(config.feed, math.floor(H / 4))
    local firstRow = 3
    local lastRow = H - feedRows - 2
    local perPage = math.max(1, lastRow - firstRow + 1)
    local pages = math.max(1, math.ceil(#trains / perPage))
    page = page % pages

    for i = 1, perPage do
        local t = trains[page * perPage + i]
        if not t then break end
        local state, color = classify(t)
        local eta = etaOf(t)
        row(firstRow + i - 1, nameOf(t), state, whereOf(t), eta and duration(eta) or "-",
            string.format("%.1f", math.abs(t.speed) * 20), notesOf(t), color)
    end
    if #trains == 0 then put(firstRow, " no trains on a track", colors.lightGray) end

    local hint = " e emergency stop all, r release them"
    if pages > 1 then
        hint = string.format(" page %d of %d, touch or any other key for the next,", page + 1, pages) .. hint
    end
    put(lastRow + 1, hint, colors.lightGray)
    put(lastRow + 2, " EVENTS", colors.black, colors.lightGray)
    for i = 1, feedRows do
        local e = feed[#feed - i + 1]
        if not e then break end
        put(lastRow + 2 + i, " " .. e.time .. "  " .. e.text, e.color)
    end
end

local ok, err = pcall(function()
    loconautics.watchNetwork()
    update()
    draw()
    local timer = os.startTimer(config.refresh)
    while true do
        local event, a = os.pullEvent()
        if event == "timer" and a == timer then
            update()
            if os.clock() - lastFlip >= config.page then
                page, lastFlip = page + 1, os.clock()
            end
            draw()
            timer = os.startTimer(config.refresh)
        elseif event == "train_signal_passed_at_danger" then
            danger[a] = os.clock() + 30
            log(nameFor(a) .. " passed a red signal", colors.red)
        elseif event == "train_added" then
            log("train added: " .. nameFor(a), colors.cyan)
        elseif event == "train_removed" then
            log("train removed: " .. nameFor(a), colors.cyan)
        elseif event == "char" and a == "e" then
            log("EMERGENCY STOP sent to " .. loconautics.stopTrains("*", true) .. " trains", colors.red)
            draw()
        elseif event == "char" and a == "r" then
            log("released " .. loconautics.releaseTrains() .. " trains", colors.lime)
            draw()
        elseif event == "monitor_touch" or event == "mouse_click" or event == "char" then
            page, lastFlip = page + 1, os.clock()
            draw()
        elseif event == "term_resize" or event == "monitor_resize" then
            draw()
        end
    end
end)

loconautics.unwatchNetwork()
out.setBackgroundColor(colors.black)
out.setTextColor(colors.white)
out.clear()
out.setCursorPos(1, 1)
if not ok and err ~= "Terminated" then printError(err) end
