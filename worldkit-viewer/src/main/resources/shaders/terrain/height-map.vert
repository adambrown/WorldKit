#version 330

uniform mat4 modelViewProjectionMatrix;
uniform float heightScale;
uniform sampler2D heightMapTexture;

in vec2 position;
in vec2 uv;

out VertexData {
    vec2 position;
    vec2 uv;
} VertexOut;

void main () {
    VertexOut.uv = uv;
    VertexOut.position = position;
    float height = texture(heightMapTexture, uv).r * heightScale;
    gl_Position = modelViewProjectionMatrix * vec4(position, height, 1.0);
}