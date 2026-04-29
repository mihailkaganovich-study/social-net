-- Tarantool in-memory storage for dialogs + Lua UDFs.
-- Designed to be called from the Spring app via Tarantool client.

box.cfg{
    listen = 3301,
    log_level = 5
}

-- Space: dialog_messages
-- Tuple format:
--  1: dialog_id         (string)
--  2: created_at_rev_ms (-created_at_ms, integer, used for DESC ordering)
--  3: message_id        (string)
--  4: from_user_id      (string)
--  5: to_user_id        (string)
--  6: text               (string)
--  7: created_at_ms     (unsigned integer)
--  8: read_at_ms        (unsigned integer, 0 means NULL/unread)
box.once('init_dialog_messages_space', function()
    local space = box.space.dialog_messages
    if space ~= nil then
        return
    end

    space = box.schema.space.create('dialog_messages')
    space:format({
        {name = 'dialog_id', type = 'string'},
        {name = 'created_at_rev_ms', type = 'integer'},
        {name = 'message_id', type = 'string'},
        {name = 'from_user_id', type = 'string'},
        {name = 'to_user_id', type = 'string'},
        {name = 'text', type = 'string'},
        {name = 'created_at_ms', type = 'unsigned'},
        {name = 'read_at_ms', type = 'unsigned'},
    })

    -- Order by created_at DESC (via created_at_rev_ms ASC)
    space:create_index('primary', {
        parts = {
            {field = 1, type = 'string'},
            {field = 2, type = 'integer'},
            {field = 3, type = 'string'},
        },
        unique = true
    })

    -- For fast "mark unread messages as read"
    space:create_index('by_dialog_to_read', {
        parts = {
            {field = 1, type = 'string'},
            {field = 5, type = 'string'},
            {field = 8, type = 'unsigned'},
        },
        unique = false
    })
end)

-- Space: dialogs (only metadata; currently not used by HTTP endpoints)
box.once('init_dialogs_space', function()
    local space = box.space.dialogs
    if space ~= nil then
        return
    end

    space = box.schema.space.create('dialogs')
    space:format({
        {name = 'dialog_id', type = 'string'},
        {name = 'user1_id', type = 'string'},
        {name = 'user2_id', type = 'string'},
        {name = 'created_at_ms', type = 'unsigned'},
        {name = 'last_message_at_ms', type = 'unsigned'},
    })
    space:create_index('primary', {
        parts = {{field = 1, type = 'string'}},
        unique = true
    })
end)

local function dialog_save_message(dialog_id, message_id, from_user_id, to_user_id, text, created_at_ms)
    local created_at_ms_num = tonumber(created_at_ms)
    local created_at_rev_ms = -created_at_ms_num

    -- read_at_ms: 0 means unread (NULL in SQL version)
    box.space.dialog_messages:insert({
        dialog_id,
        created_at_rev_ms,
        message_id,
        from_user_id,
        to_user_id,
        text,
        created_at_ms_num,
        0
    })

    -- Update dialog metadata (user1_id/user2_id are ordered deterministically)
    local user1 = from_user_id
    local user2 = to_user_id
    if user1 > user2 then
        user1, user2 = user2, user1
    end

    local existing = box.space.dialogs:get({dialog_id})
    if existing == nil then
        box.space.dialogs:insert({dialog_id, user1, user2, created_at_ms_num, created_at_ms_num})
    else
        -- Field #5: last_message_at_ms
        box.space.dialogs:update({dialog_id}, {{'=', 5, created_at_ms_num}})
    end

    return message_id
end

local function dialog_get_messages(dialog_id, limit, offset)
    limit = tonumber(limit) or 50
    offset = tonumber(offset) or 0

    local result = {}
    local skipped = 0
    local taken = 0

    -- Iterate by prefix dialog_id in primary index order (created_at_rev_ms ASC => created_at DESC)
    for _, tuple in box.space.dialog_messages.index.primary:pairs(dialog_id) do
        if skipped < offset then
            skipped = skipped + 1
        else
            local read_at_ms = tuple[8]
            result[#result + 1] = {
                tuple[1], -- dialog_id
                tuple[3], -- message_id
                tuple[4], -- from_user_id
                tuple[5], -- to_user_id
                tuple[6], -- text
                tuple[7], -- created_at_ms
                read_at_ms ~= 0 and read_at_ms or nil
            }

            taken = taken + 1
            if taken >= limit then
                break
            end
        end
    end

    return result
end

local function dialog_mark_as_read(dialog_id, user_id, read_at_ms)
    local read_at_ms_num = tonumber(read_at_ms)
    local tuples = box.space.dialog_messages.index.by_dialog_to_read:select({dialog_id, user_id, 0})

    for _, tuple in ipairs(tuples) do
        -- Primary key is (dialog_id, created_at_rev_ms, message_id)
        box.space.dialog_messages:update(
            {tuple[1], tuple[2], tuple[3]},
            {{'=', 8, read_at_ms_num}} -- Field #8: read_at_ms
        )
    end

    return #tuples
end

box.once('init_dialog_udfs', function()
    box.schema.func.create('dialog_save_message', {if_not_exists = true})
    box.func.dialog_save_message = dialog_save_message

    box.schema.func.create('dialog_get_messages', {if_not_exists = true})
    box.func.dialog_get_messages = dialog_get_messages

    box.schema.func.create('dialog_mark_as_read', {if_not_exists = true})
    box.func.dialog_mark_as_read = dialog_mark_as_read
end)

-- Script ends here; Tarantool keeps running serving requests.

