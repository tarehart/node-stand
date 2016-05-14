(function() {
    'use strict';

    angular
        .module('nodeStandControllers')
        .provider('markdownConverter', markdownConverter)
        .directive("markdownEditor", ['$sanitize', 'markdownConverter', markdownEditor])
        .directive("renderMarkdown", ['$sanitize', 'markdownConverter', renderMarkdown]);

    function markdownConverter() {
        return {
            $get: function () {
                var opts = {
                    extensions: ['finer-points-markdown']
                };
                return new showdown.Converter(opts);
            }
        };
    }

    function markdownEditor($sanitize, markdownConverter) {
        return {
            restrict: "A",
            scope: {
                node: "=",
                setText: "=",
                linkFn: "="
            },
            link:     function (scope, element, attrs, ngModel) {

                var additionalButtons = [];

                if (scope.linkFn) {
                    additionalButtons.push({
                        name: 'groupNode',
                        data: [{
                            name: 'nodelink',
                            toggle: false,
                            hotkey: 'Ctrl+K',
                            title: 'Link Node',
                            btnText: 'Link Node',
                            btnClass: 'btn btn-primary btn-sm',
                            icon: { glyph: 'glyphicon glyphicon-link', fa: 'fa fa-link', 'fa-3': 'icon-link' },
                            callback: function(e){
                                var selection = e.getSelection();

                                // The performReplace function will be called much later after some external
                                // code decides what node should be inserted.
                                function performReplace(nodeId, nodeTitle) {
                                    var tagText = selection.text || nodeTitle;
                                    e.replaceSelection("{{[" + nodeId + "]" + tagText + "}}");
                                    var offset = ("" + nodeId).length + 4;
                                    e.setSelection(selection.start + offset, selection.start + offset + tagText.length);
                                    scope.setText(scope.node, e.getContent());
                                }
                                scope.linkFn(scope.node, performReplace);
                            }
                        }]
                    });
                }

                $(element).markdown({
                    savable:false,
                    onChange: function(e){
                        var text = e.getContent();
                        // TODO: sanitize
                        scope.setText(scope.node, text);
                        scope.$apply();
                    },
                    hiddenButtons: ['Preview', 'Image', 'cmdUrl'],
                    fullscreen: {enable: false},
                    iconlibrary: "fa", // Use font-awesome
                    additionalButtons: [additionalButtons]
                });
            }
        }
    }

    function renderMarkdown($sanitize, markdownConverter) {
        return {
            restrict: "A",
            scope: {
                ngMarkdown: "="
            },
            template: '<div ng-bind-html="html"></div>',
            link: function (scope, element, attrs, ngModel) {
                scope.$watch(function(scope) {return scope.ngMarkdown;}, function (v) {
                    if (!v) {
                        v = "";
                    }

                    var rawHtml = markdownConverter.makeHtml(v);
                    scope.html = rawHtml;

                }, true);
            }
        }
    }

    // This used to live in js/local/showdown-node-stand.js
    var markdownExtension = function(a) {
        return [{
            type: "lang",
            regex: /{{\[([0-9a-z]{1,25})\](.+?)(?=}})}}/g,
            replace: function(a, b, c) {
                return '<span class="node-link"><span class="node-id">' + b + '</span>' + c + '</span>';
            }
        }]
    };
    typeof window != "undefined" && window.showdown && window.showdown.extension && (window.showdown.extension('finer-points-markdown', markdownExtension))


})();