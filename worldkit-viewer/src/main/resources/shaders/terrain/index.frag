#version 330

uniform sampler2D indexTexture;
uniform sampler2D colorLutTexture;

in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    float id = texture(indexTexture, VertexIn.uv).r;
    colorOut = texture(colorLutTexture, vec2(id, 0.5));
}
