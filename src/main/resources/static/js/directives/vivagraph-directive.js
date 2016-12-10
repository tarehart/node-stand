require('../../sass/vivagraph.scss');

(function() {
    'use strict';

    var Viva = require('../lib/vivagraph');

    angular
        .module('nodeStandControllers')
        .directive('vivaGraph', ['$rootScope', vivaGraph]);

    function vivaGraph($rootScope) {
        return {
            restrict: "A",
            scope: {
                rootNode: "="
            },
            templateUrl: "partials/viva-graph.html",
            link: function (scope, element) {
                setupEventHandlers(scope, element, $rootScope);
            }
        }
    }


    function setupEventHandlers(scope, element, $rootScope) {

        var graph = Viva.Graph.graph();

        scope.highlightedNode = null;

        var vivaContainer = element.find("#viva-container")[0];

        var layout = Viva.Graph.Layout.forceDirected(graph, {
            springLength : 45, // default is 30
            springCoeff : 0.0008, // default is 0.0008, higher coeff = more stiffness
            dragCoeff : 0.03, // default is 0.02
            gravity : -1.2 // default is -1.2. More negative is more node repulsion.
        });

        function appendVoteArcsFromNode(ui, argumentNode) {
            var majorVersion = argumentNode.body.majorVersion;
            var radius = getRadius(argumentNode) + 2;
            appendVoteArcs(ui, radius, majorVersion.greatVotes, majorVersion.weakVotes, majorVersion.toucheVotes, majorVersion.trashVotes);
        }
        
        function appendVoteArcs(ui, r, greatVotes, weakVotes, toucheVotes, trashVotes) {

            var totalVotes = greatVotes + weakVotes + toucheVotes + trashVotes;
            if (totalVotes === 0) {
                return;
            }

            var radsPerVote = (Math.PI * 2) / totalVotes;
            var currentRads = Math.PI; // Rotate the whole thing so the colors land in pleasant locations. Normally this would be 0.

            if (greatVotes) {
                ui.append(makeArcPath(r, currentRads, currentRads += greatVotes * radsPerVote, 'voteArc greatArc'));
            }

            if (weakVotes) {
                ui.append(makeArcPath(r, currentRads, currentRads += weakVotes * radsPerVote, 'voteArc weakArc'));
            }

            if (toucheVotes) {
                ui.append(makeArcPath(r, currentRads, currentRads += toucheVotes * radsPerVote, 'voteArc toucheArc'));
            }

            if (trashVotes) {
                ui.append(makeArcPath(r, currentRads, currentRads + trashVotes * radsPerVote, 'voteArc trashArc'));
            }

        }

        function makeArcPath(radius, startRadians, endRadians, className) {
            return Viva.Graph.svg('path', {
                d: makeArc(radius, startRadians, endRadians),
                class: className,
                fill: 'none'
            });
        }

        function makeArc(radius, startRadians, endRadians) {
            // Stare at this: https://www.w3.org/TR/SVG/paths.html#PathDataEllipticalArcCommands
            // This should produce a string like "M15,0 A15,15 0 0,1 0,15"
            // In other words, "Move to coordinate (15, 0) and using an ellipse with dimensions 15,15 (i.e. a circle with radius 15),
            // arc toward coordinate (0, 15)." There are some other numbers in the middle, see the link.

            var largeArcFlag = endRadians - startRadians > Math.PI ? 1 : 0;

            return 'M' + radius * Math.cos(startRadians) + ',' + radius * Math.sin(startRadians) +
                ' A' + radius + ',' + radius + ' 0 ' + largeArcFlag + ',1 ' + radius * Math.cos(endRadians) + ',' + radius * Math.sin(endRadians);
        }

        function getRadius(argumentNode) {
            var type = argumentNode.getType();
            return type === 'assertion' ? 14 :
                type === 'interpretation' ? 11 : 9;
        }

        var graphics = Viva.Graph.View.svgGraphics();
        graphics.node(function(node) {
            // The function is called every time renderer needs a ui to display node

            var argumentNode = node.data.node;
            var type = argumentNode.getType();

            var ui = Viva.Graph.svg('g', {
                    class: 'vivaDot ' + type + 'Dot' + (node.data.isRoot ? ' rootDot' : '')
                }),
                circle = Viva.Graph.svg('circle', {
                    r: getRadius(argumentNode)
                });

            ui.append(circle);

            appendVoteArcsFromNode(ui, argumentNode);

            circle.addEventListener('click', tapListener);
            circle.addEventListener('touchend', tapListener);

            function tapListener() {
                layout.pinNode(node, true);
                // Broadcasting downward from root, because sibling scopes need to know about the highlight.
                $rootScope.$broadcast("nodeHighlighted", node.data.node);

                $rootScope.$apply();
            }

            return ui;

        }).placeNode(function(nodeUI, pos){
            // https://github.com/anvaka/VivaGraphJS/blob/master/demos/tutorial_svg/06%20-%20Composite%20Nodes.html
            // 'g' element doesn't have convenient (x,y) attributes, instead
            // we have to deal with transforms: http://www.w3.org/TR/SVG/coords.html#SVGGlobalTransformAttribute
            nodeUI.attr('transform', 'translate(' + pos.x + ',' + pos.y + ')');
        });

        function makeRenderer(forFullscreen) {
            return Viva.Graph.View.renderer(graph, {
                container  : vivaContainer,
                graphics: graphics,
                layout: layout,
                interactive: forFullscreen? 'scroll drag node': 'node' // Only allow zooming and panning in fullscreen
            });
        }

        var renderer = makeRenderer(false);
        renderer.run();

        if (scope.rootNode) {
            addVivaNodesRecursive(scope.rootNode, null, graph, {});
        }

        scope.$on("rootData", function(event, rootNode) {
            addVivaNodesRecursive(rootNode, null, graph, {});
        });

        scope.$on("nodeAdded", function(event, parent, child) {
            addVivaNodesRecursive(child, parent, graph, {});
        });

        scope.$on("edgeAdded", function(event, parent, child) {
            graph.addLink(parent.id, child.id);
        });

        scope.$on("edgeRemoved", function(event, parent, child) {

            var linkToRemove = null;

            graph.forEachLinkedNode(parent.id, function(linkedNode, link){
                if (linkedNode.id === child.id) {
                    linkToRemove = link;
                }
            });

            graph.removeLink(linkToRemove);
        });

        scope.$on("nodeHighlighted", function(e, node) {
            if (node !== scope.highlightedNode) {
                if (scope.highlightedNode) {
                    var oldUI = graphics.getNodeUI(scope.highlightedNode.id);
                    $(oldUI).find('.highlight').remove();
                }

                var nodeUI = graphics.getNodeUI(node.id);
                if (nodeUI) {
                    nodeUI.append(
                        Viva.Graph.svg('circle', {
                            class: 'highlight',
                            r: getRadius(node) - 5
                        })
                    );


                }
                scope.highlightedNode = node;
            }
        });

        scope.$on("voteChanged", function(e, node) {
            var ui = graphics.getNodeUI(node.id);
            $(ui).find('.voteArc').remove();
            appendVoteArcsFromNode(ui, node);
        });

        $(document).on('webkitfullscreenchange mozfullscreenchange fullscreenchange MSFullscreenChange', handleFullscreenChange);

        function handleFullscreenChange() {
            var fullscreenEl =
                document.isFullscreen ||
                document.fullscreenElement ||
                document.webkitFullscreenElement ||
                document.mozFullscreenElement ||
                document.msFullscreenElement;

            scope.fullscreen = !!fullscreenEl;

            console.log("scope.fullscreen: " + scope.fullscreen);

            renderer.dispose();
            renderer = makeRenderer(scope.fullscreen); // Get a renderer with the appropriate interactivity
            renderer.run();

            // Give the browser a moment to get to the right dimensions.
            setTimeout(function() {
                renderer.reset(); // This will cause the graph to re-center.
            }, 200);


            scope.$apply();
        }

        scope.goFullscreen = function() {

            var elem = vivaContainer;
            if (elem.requestFullscreen) {
                elem.requestFullscreen();
            } else if (elem.msRequestFullscreen) {
                elem.msRequestFullscreen();
            } else if (elem.mozRequestFullScreen) {
                elem.mozRequestFullScreen();
            } else if (elem.webkitRequestFullscreen) {
                elem.webkitRequestFullscreen();
            }
        };
    }

    function addVivaNodesRecursive(node, parent, graph, addedNodes) {

        if (!addedNodes[node.id]) {


            graph.addNode(node.id, {
                node : node,
                isRoot: !parent
            });

            addedNodes[node.id] = 1;

            for(var i = 0; i < node.children.length; i++) {
                addVivaNodesRecursive(node.children[i], node, graph, addedNodes);
            }
        }

        if (parent) {
            graph.addLink(parent.id, node.id);
        }

    }

})();