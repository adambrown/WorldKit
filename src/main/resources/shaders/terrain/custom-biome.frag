#version 330

uniform sampler2D map;

in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    float magnitude = texture(map, VertexIn.uv).r;
    colorOut = vec4(magnitude, magnitude, magnitude, 1.0);
}
