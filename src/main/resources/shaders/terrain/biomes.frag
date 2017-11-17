#version 330

uniform sampler2D biomeTexture;
uniform sampler2D splineTexture;
uniform sampler2D biomeColorsTexture;

in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    int id = int(texture(biomeTexture, VertexIn.uv).r * 256);
    vec3 splineColor = texture(splineTexture, VertexIn.uv).rgb;
    float alpha = splineColor.g;
    float iAlpha = 1.0 - alpha;
    float green = min(splineColor.r, splineColor.b);
    vec3 fixedColor = vec3(splineColor.r, green, splineColor.b);
    if (id == 0) {
        colorOut = vec4(0.0, 0.0, 0.0, 1.0);
    } else {
        vec3 color = texture(biomeColorsTexture, vec2(mod(id - 1, 8) * 0.14285714, ((id - 1) / 8) * 0.14285714)).rgb;
        colorOut = vec4((color * iAlpha + fixedColor * alpha), 1.0);
    }
}
