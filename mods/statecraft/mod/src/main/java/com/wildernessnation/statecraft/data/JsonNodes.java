package com.wildernessnation.statecraft.data;

import com.wildernessnation.statecraft.core.persist.StateNode;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Map;

/**
 * Gson 的 {@link JsonElement} → {@link StateNode}：机械翻译，不含任何规则。
 *
 * <p>数字的整数/小数之分靠字面量判断（有没有 {@code . / e / E}）。
 * 于是 `12` 是整数、`12.0` 是小数——<strong>给整数字段写 `12.0` 会报出字段名</strong>，
 * 而不是被悄悄截断（宁可报错也不要静默丢东西）。
 */
public final class JsonNodes {

    private JsonNodes() {}

    public static StateNode fromJson(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            Map<String, StateNode> fields = StateNode.fields();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                fields.put(e.getKey(), fromJson(e.getValue()));
            }
            return new StateNode.Obj(fields);
        }
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            java.util.List<StateNode> items = new java.util.ArrayList<>(arr.size());
            for (JsonElement e : arr) {
                items.add(fromJson(e));
            }
            return new StateNode.Arr(items);
        }
        JsonPrimitive p = element.getAsJsonPrimitive();
        if (p.isString()) {
            return new StateNode.Str(p.getAsString());
        }
        if (p.isBoolean()) {
            return new StateNode.Bool(p.getAsBoolean());
        }
        String raw = p.getAsString();
        boolean fractional = raw.indexOf('.') >= 0 || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0;
        return fractional
                ? new StateNode.Dec(p.getAsDouble())
                : new StateNode.Int(p.getAsLong());
    }
}
