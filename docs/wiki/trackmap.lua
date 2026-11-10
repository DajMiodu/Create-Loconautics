local config = {
    monitor = nil,
    dimension = "minecraft:overworld",
    box = nil,
    pad = 48,
    refresh = 1,
    scale = 0.5,
}

local USAGE = "trackmap [monitor=top] [dimension=minecraft:overworld] [box=minX,minZ,maxX,maxZ] [pad=48] [refresh=1] [scale=0.5]"
local NUMBERS = { pad = true, refresh = true, scale = true }

for _, arg in ipairs({ ... }) do
    local key, value = arg:match("^(%w+)=(.+)$")
    if not key or config[key] == nil and key ~= "monitor" and key ~= "box" then
        error("unknown option '" .. arg .. "', use: " .. USAGE, 0)
    end
    if NUMBERS[key] then
        config[key] = tonumber(value) or error(key .. " must be a number", 0)
    elseif key == "box" then
        local parts = {}
        for number in value:gmatch("[^,]+") do parts[#parts + 1] = tonumber(number) end
        if #parts ~= 4 then error("box needs four numbers: minX,minZ,maxX,maxZ", 0) end
        config.box = parts
    else
        config[key] = value
    end
end

local DIMENSION = config.dimension
local TILE_MIN = 16

local WHITE, ORANGE, MAGENTA, LIGHT_BLUE, YELLOW, LIME, PINK, GRAY = 0, 1, 2, 3, 4, 5, 6, 7
local CYAN, PURPLE, RED, BLACK = 9, 10, 14, 15

local PRIORITY = { [BLACK] = 0, [GRAY] = 1 }
for _, c in ipairs({ WHITE, ORANGE, MAGENTA, LIGHT_BLUE, YELLOW, LIME, PINK, CYAN, PURPLE, RED }) do
    PRIORITY[c] = 2
end

local mon
if config.monitor then
    mon = peripheral.wrap(config.monitor)
else
    mon = peripheral.find("monitor")
end
if not mon then error("no monitor found, pass monitor=<side or name>", 0) end

local function boundsOf()
    local minX, minZ, maxX, maxZ = math.huge, math.huge, -math.huge, -math.huge
    local function add(x, z)
        minX, maxX = math.min(minX, x), math.max(maxX, x)
        minZ, maxZ = math.min(minZ, z), math.max(maxZ, z)
    end
    for _, list in ipairs({
        loconautics.getTrains(), loconautics.getSignals(),
        loconautics.getJunctions(), loconautics.getTurntables(),
    }) do
        for _, e in ipairs(list) do
            if e.dimension == DIMENSION and e.x then add(e.x, e.z) end
        end
    end
    if minX == math.huge then error("nothing to map in " .. DIMENSION, 0) end
    return minX - config.pad, minZ - config.pad, maxX + config.pad, maxZ + config.pad
end

local minX, minZ, maxX, maxZ
if config.box then
    minX, minZ, maxX, maxZ = table.unpack(config.box)
else
    minX, minZ, maxX, maxZ = boundsOf()
end

local function region(extra)
    local r = { dimension = DIMENSION, minX = minX, maxX = maxX, minZ = minZ, maxZ = maxZ }
    for k, v in pairs(extra or {}) do r[k] = v end
    return r
end

local nodes, edges, edgeSeen = {}, {}, {}

local function fetch(x0, z0, x1, z1)
    local ok, res = pcall(loconautics.getTrack,
        { dimension = DIMENSION, minX = x0, maxX = x1, minZ = z0, maxZ = z1 })
    if not ok then
        if not tostring(res):find("track nodes", 1, true) then error(res, 0) end
        if x1 - x0 <= TILE_MIN then return end
        local mx, mz = (x0 + x1) / 2, (z0 + z1) / 2
        fetch(x0, z0, mx, mz)
        fetch(mx, z0, x1, mz)
        fetch(x0, mz, mx, z1)
        fetch(mx, mz, x1, z1)
        return
    end
    for _, n in ipairs(res.nodes) do nodes[n.id] = n end
    for _, e in ipairs(res.edges) do
        local key = e.from .. ">" .. e.to
        if not edgeSeen[key] then
            edgeSeen[key] = true
            edges[#edges + 1] = e
        end
    end
end

mon.setTextScale(config.scale)
mon.setBackgroundColor(colors.black)
mon.clear()
local CW, CH = mon.getSize()
local W, H = CW * 2, CH * 3

local spanX, spanZ = math.max(maxX - minX, 1), math.max(maxZ - minZ, 1)
local scale = math.min(W / spanX, H / spanZ)
local offX = (W - spanX * scale) / 2
local offZ = (H - spanZ * scale) / 2

local function toPixel(x, z)
    return math.floor((x - minX) * scale + offX), math.floor((z - minZ) * scale + offZ)
end

local base = {}
for y = 1, H do
    local row = {}
    for x = 1, W do row[x] = BLACK end
    base[y] = row
end

local over, overKeys, dirty = {}, {}, {}

local function markDirty(x, y)
    if x < 0 or x >= W or y < 0 or y >= H then return end
    local cx, cy = math.floor(x / 2) + 1, math.floor(y / 3) + 1
    dirty[cy * 1000 + cx] = { cx, cy }
end

local function putBase(x, y, color)
    if x < 0 or x >= W or y < 0 or y >= H then return end
    base[y + 1][x + 1] = color
    markDirty(x, y)
end

local function putOver(x, y, color)
    if x < 0 or x >= W or y < 0 or y >= H then return end
    local key = y * W + x
    over[key] = color
    overKeys[#overKeys + 1] = key
    markDirty(x, y)
end

local function clearOver()
    for _, key in ipairs(overKeys) do
        over[key] = nil
        markDirty(key % W, math.floor(key / W))
    end
    overKeys = {}
end

local function pixel(x, y)
    return over[y * W + x] or base[y + 1][x + 1]
end

local function line(x0, y0, x1, y1, color, plot)
    local dx, dy = math.abs(x1 - x0), -math.abs(y1 - y0)
    local sx = x0 < x1 and 1 or -1
    local sy = y0 < y1 and 1 or -1
    local err = dx + dy
    while true do
        plot(x0, y0, color)
        if x0 == x1 and y0 == y1 then return end
        local e2 = 2 * err
        if e2 >= dy then err = err + dy; x0 = x0 + sx end
        if e2 <= dx then err = err + dx; y0 = y0 + sy end
    end
end

local function square(x, y, size, color, plot)
    local half = math.floor(size / 2)
    for dy = -half, size - 1 - half do
        for dx = -half, size - 1 - half do
            plot(x + dx, y + dy, color)
        end
    end
end

local function ring(x, y, size, color, plot)
    local half = math.floor(size / 2)
    for d = -half, size - 1 - half do
        plot(x + d, y - half, color)
        plot(x + d, y + size - 1 - half, color)
        plot(x - half, y + d, color)
        plot(x + size - 1 - half, y + d, color)
    end
end

local function drawEdge(e, nodeMap, plot)
    if e.portal then return end
    if e.path then
        local p = e.path
        local px, py = toPixel(p[1], p[2])
        for i = 3, #p - 1, 2 do
            local nx, ny = toPixel(p[i], p[i + 1])
            line(px, py, nx, ny, GRAY, plot)
            px, py = nx, ny
        end
        return
    end
    local a, b = nodeMap[e.from], nodeMap[e.to]
    if a and b then
        local x0, y0 = toPixel(a.x, a.z)
        local x1, y1 = toPixel(b.x, b.z)
        line(x0, y0, x1, y1, GRAY, plot)
    end
end

local SUBPIXELS = { { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 }, { 0, 2 }, { 1, 2 } }

local function cellOf(cx, cy)
    local here = {}
    local fg, best = BLACK, 0
    for i, offset in ipairs(SUBPIXELS) do
        local c = pixel((cx - 1) * 2 + offset[1], (cy - 1) * 3 + offset[2])
        here[i] = c
        if PRIORITY[c] > best then fg, best = c, PRIORITY[c] end
    end
    local bits = 0
    for i = 1, 5 do
        if here[i] == fg then bits = bits + 2 ^ (i - 1) end
    end
    local bg = BLACK
    if here[6] == fg and fg ~= BLACK then
        bits = 31 - bits
        fg, bg = BLACK, fg
    end
    return string.char(128 + bits), string.format("%x", fg), string.format("%x", bg)
end

local function drawAll()
    for cy = 1, CH do
        local text, fgs, bgs = {}, {}, {}
        for cx = 1, CW do
            text[cx], fgs[cx], bgs[cx] = cellOf(cx, cy)
        end
        mon.setCursorPos(1, cy)
        mon.blit(table.concat(text), table.concat(fgs), table.concat(bgs))
    end
    dirty = {}
end

local function flush()
    for _, cell in pairs(dirty) do
        local c, f, b = cellOf(cell[1], cell[2])
        mon.setCursorPos(cell[1], cell[2])
        mon.blit(c, f, b)
    end
    dirty = {}
end

local function inBox(e)
    return e.dimension == DIMENSION and e.x >= minX and e.x <= maxX and e.z >= minZ and e.z <= maxZ
end

fetch(minX, minZ, maxX, maxZ)

for i, e in ipairs(edges) do
    if e.portal then
        for _, id in ipairs({ e.from, e.to }) do
            local n = nodes[id]
            if n and n.dimension == DIMENSION then
                local x, y = toPixel(n.x, n.z)
                square(x, y, 3, PURPLE, putBase)
            end
        end
    elseif not e.sublevel then
        drawEdge(e, nodes, putBase)
    end
    if i % 200 == 0 then sleep(0) end
end

local signalColor = {}

local function signalDotColor(s)
    local color = LIME
    for _, side in ipairs(s.sides) do
        if side.state == "red" then return RED end
        if side.state == "yellow" then color = YELLOW end
    end
    return color
end

local function drawFixed()
    for _, s in ipairs(loconautics.getSignals(region())) do
        if not s.sublevel then
            local color = signalDotColor(s)
            if signalColor[s.id] ~= color then
                signalColor[s.id] = color
                local x, y = toPixel(s.x, s.z)
                square(x, y, 2, color, putBase)
            end
        end
    end
end

local function drawMoving()
    local ok, res = pcall(loconautics.getTrack, region({ subOnly = true }))
    if ok then
        local subNodes = {}
        for _, n in ipairs(res.nodes) do subNodes[n.id] = n end
        for _, e in ipairs(res.edges) do drawEdge(e, subNodes, putOver) end
    end
    for _, s in ipairs(loconautics.getStations()) do
        if s.x and inBox(s) then
            local x, y = toPixel(s.x, s.z)
            square(x, y, 3, PINK, s.sublevel and putOver or putBase)
        end
    end
    for _, s in ipairs(loconautics.getSignals(region())) do
        if s.sublevel then
            local x, y = toPixel(s.x, s.z)
            square(x, y, 2, signalDotColor(s), putOver)
        end
    end
    for _, t in ipairs(loconautics.getTurntables(region())) do
        local x, y = toPixel(t.x, t.z)
        ring(x, y, 5, CYAN, putOver)
        if t.occupied then square(x, y, 3, CYAN, putOver) end
    end
    for _, t in ipairs(loconautics.getTrains()) do
        if t.dimension == DIMENSION and t.x then
            local color = LIGHT_BLUE
            if t.derailed then color = MAGENTA
            elseif t.heldAtRed then color = ORANGE
            elseif math.abs(t.speed) < 0.01 then color = WHITE end
            local x, y = toPixel(t.x, t.z)
            square(x, y, 3, color, putOver)
        end
    end
end

local function refresh()
    clearOver()
    drawMoving()
    flush()
end

drawFixed()
drawAll()

print(string.format("%s, %d nodes, %d edges, %d signals, %d trains",
    DIMENSION, #nodes, #edges, #loconautics.getSignals(region()), #loconautics.getTrains()))

local ok, err = pcall(function()
    loconautics.watchNetwork()
    refresh()
    local timer = os.startTimer(config.refresh)
    while true do
        local event, a = os.pullEvent()
        if event == "timer" and a == timer then
            refresh()
            timer = os.startTimer(config.refresh)
        elseif event == "signal_changed" then
            drawFixed()
            flush()
        end
    end
end)

loconautics.unwatchNetwork()
mon.setBackgroundColor(colors.black)
mon.setTextColor(colors.white)
mon.clear()
if not ok and err ~= "Terminated" then printError(err) end
