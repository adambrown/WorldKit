#version 330

uniform mat4 modelViewProjectionMatrix;

in vec2 position;

out VertexData {
    vec2 uv;
} VertexOut;

void main () {
    VertexOut.uv = vec2(position.x, position.y);
    gl_Position = modelViewProjectionMatrix * vec4(position, 0.0f, 1.0f);
}