#version 410

uniform vec4 lightPosition;
uniform mat4 modelViewProjectionMatrix;
uniform mat4 modelViewMatrix;
uniform mat4 normalMatrix;

in vec3 position;
in vec3 normal;

out VertexData {
    vec3 lightVector;
    vec3 normal;
    vec3 eye;
} VertexOut;

void main () {
    vec4 pos = modelViewMatrix * vec4(position, 1.0);
    VertexOut.lightVector = vec3(lightPosition - pos);
    VertexOut.normal = vec3(normalize(normalMatrix * vec4(normal, 0.0)));
    VertexOut.eye = vec3(-pos);
    gl_Position = modelViewProjectionMatrix * vec4(position, 1.0);
}